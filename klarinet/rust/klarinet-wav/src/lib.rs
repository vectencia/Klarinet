//! WAV codec used by the JS wasm exports and the JVM file path.
//!
//! [`encode_wav16`] and [`decode_wav`] are the one-shot API. The JVM reader
//! and writer use [`WavReader`] and [`WavWriter`], which share the same
//! 16-bit and float conversion. Other bit depths stay on the caller's
//! existing decoder.

use std::io::{Read, Seek, SeekFrom, Write};

/// PCM produced by [`decode_wav`].
#[derive(Debug, Clone, PartialEq)]
pub struct DecodedWav {
    pub samples: Vec<f32>,
    pub sample_rate: i32,
    pub channel_count: i32,
}

/// Why a WAV buffer was rejected.
#[derive(Debug)]
pub enum WavError {
    NotWav,
    MissingData,
    InvalidFmt,
    Unsupported { format: i32, bits: i32 },
    Io(std::io::Error),
}

/// Encode interleaved float samples as 16-bit PCM WAV.
pub fn encode_wav16(
    samples: &[f32],
    sample_rate: i32,
    channel_count: i32,
) -> Result<Vec<u8>, WavError> {
    let mut writer = WavWriter::new(std::io::Cursor::new(Vec::new()), sample_rate, channel_count)?;
    writer.write_frames(samples)?;
    Ok(writer.finish()?.into_inner())
}

/// Decode a 16-bit PCM or 32-bit float WAV buffer.
pub fn decode_wav(bytes: &[u8]) -> Result<DecodedWav, WavError> {
    let mut reader = WavReader::open(std::io::Cursor::new(bytes))?;
    let mut samples = vec![0.0f32; reader.remaining_samples()];
    let got = reader.read_samples(&mut samples)?;
    samples.truncate(got);
    Ok(DecodedWav {
        samples,
        sample_rate: reader.sample_rate(),
        channel_count: reader.channel_count(),
    })
}

/// Streaming 16-bit PCM writer. [`encode_wav16`] is this writer backed by a buffer.
pub struct WavWriter<W: Write + Seek> {
    inner: W,
    sample_rate: i32,
    channel_count: i32,
    data_bytes: u32,
    finished: bool,
}

impl<W: Write + Seek> WavWriter<W> {
    pub fn new(inner: W, sample_rate: i32, channel_count: i32) -> Result<Self, WavError> {
        let mut writer = WavWriter {
            inner,
            sample_rate,
            channel_count,
            data_bytes: 0,
            finished: false,
        };
        writer.write_header(0)?;
        Ok(writer)
    }

    pub fn write_frames(&mut self, samples: &[f32]) -> Result<(), WavError> {
        if self.finished {
            return Err(WavError::InvalidFmt);
        }
        for sample in samples {
            let pcm = sample_to_pcm16(*sample);
            self.inner
                .write_all(&pcm.to_le_bytes())
                .map_err(WavError::Io)?;
        }
        let added = u32::try_from(samples.len())
            .ok()
            .and_then(|count| count.checked_mul(2))
            .ok_or(WavError::InvalidFmt)?;
        self.data_bytes = self
            .data_bytes
            .checked_add(added)
            .ok_or(WavError::InvalidFmt)?;
        self.sync_header()
    }

    pub fn flush_header(&mut self) -> Result<(), WavError> {
        if self.finished {
            return Ok(());
        }
        self.sync_header()
    }

    pub fn finish(mut self) -> Result<W, WavError> {
        if !self.finished {
            self.sync_header()?;
            self.finished = true;
        }
        Ok(self.inner)
    }

    fn sync_header(&mut self) -> Result<(), WavError> {
        let end = 44u64.saturating_add(self.data_bytes as u64);
        self.inner.seek(SeekFrom::Start(0)).map_err(WavError::Io)?;
        self.write_header(self.data_bytes)?;
        self.inner
            .seek(SeekFrom::Start(end))
            .map_err(WavError::Io)?;
        self.inner.flush().map_err(WavError::Io)?;
        Ok(())
    }

    fn write_header(&mut self, data_bytes: u32) -> Result<(), WavError> {
        let mut header = [0u8; 44];
        header[0..4].copy_from_slice(b"RIFF");
        write_u32(&mut header, 4, 36u32.saturating_add(data_bytes));
        header[8..12].copy_from_slice(b"WAVE");
        header[12..16].copy_from_slice(b"fmt ");
        write_u32(&mut header, 16, 16);
        write_u16(&mut header, 20, 1);
        write_u16(&mut header, 22, self.channel_count as u16);
        write_u32(&mut header, 24, self.sample_rate as u32);
        let byte_rate = (self.sample_rate as u32)
            .saturating_mul(self.channel_count as u32)
            .saturating_mul(2);
        write_u32(&mut header, 28, byte_rate);
        write_u16(
            &mut header,
            32,
            (self.channel_count.saturating_mul(2)) as u16,
        );
        write_u16(&mut header, 34, 16);
        header[36..40].copy_from_slice(b"data");
        write_u32(&mut header, 40, data_bytes);
        self.inner.write_all(&header).map_err(WavError::Io)?;
        Ok(())
    }
}

/// Streaming reader for the layouts [`decode_wav`] accepts.
pub struct WavReader<R: Read + Seek> {
    inner: R,
    sample_rate: i32,
    channel_count: i32,
    bytes_per_sample: usize,
    data_start: u64,
    data_end: u64,
    position: u64,
}

impl<R: Read + Seek> WavReader<R> {
    pub fn open(mut inner: R) -> Result<Self, WavError> {
        let mut header = [0u8; 12];
        inner
            .read_exact(&mut header)
            .map_err(|_| WavError::NotWav)?;
        if &header[0..4] != b"RIFF" || &header[8..12] != b"WAVE" {
            return Err(WavError::NotWav);
        }
        let mut format = 1i32;
        let mut channels = 1i32;
        let mut sample_rate = 44100i32;
        let mut bits = 16i32;
        let mut saw_fmt = false;
        let mut data_start = None;
        let mut data_len = None;
        loop {
            let mut chunk = [0u8; 8];
            match inner.read_exact(&mut chunk) {
                Ok(()) => {}
                Err(error) if error.kind() == std::io::ErrorKind::UnexpectedEof => break,
                Err(error) => return Err(WavError::Io(error)),
            }
            let size = u32::from_le_bytes([chunk[4], chunk[5], chunk[6], chunk[7]]) as u64;
            let start = inner.stream_position().map_err(WavError::Io)?;
            let id = [chunk[0], chunk[1], chunk[2], chunk[3]];
            let file_end = stream_len(&mut inner)?;
            if id == *b"fmt " {
                if size < 16 {
                    return Err(WavError::InvalidFmt);
                }
                let mut fmt = [0u8; 16];
                inner.read_exact(&mut fmt).map_err(WavError::Io)?;
                format = u16::from_le_bytes([fmt[0], fmt[1]]) as i32;
                channels = u16::from_le_bytes([fmt[2], fmt[3]]) as i32;
                sample_rate = i32::from_le_bytes([fmt[4], fmt[5], fmt[6], fmt[7]]);
                bits = u16::from_le_bytes([fmt[14], fmt[15]]) as i32;
                saw_fmt = true;
                skip_rest(&mut inner, size - 16, file_end)?;
            } else if id == *b"data" {
                let available = file_end.saturating_sub(start);
                data_start = Some(start);
                data_len = Some(size.min(available));
                skip_rest(&mut inner, size, file_end)?;
            } else {
                skip_rest(&mut inner, size, file_end)?;
            }
        }
        if !saw_fmt {
            return Err(WavError::MissingData);
        }
        let data_start = data_start.ok_or(WavError::MissingData)?;
        let data_len = data_len.ok_or(WavError::MissingData)?;
        if channels <= 0 || sample_rate <= 0 {
            return Err(WavError::MissingData);
        }
        let bytes_per_sample = match (format, bits) {
            (1, 16) => 2,
            (3, 32) => 4,
            _ => return Err(WavError::Unsupported { format, bits }),
        };
        inner
            .seek(SeekFrom::Start(data_start))
            .map_err(WavError::Io)?;
        Ok(WavReader {
            inner,
            sample_rate,
            channel_count: channels,
            bytes_per_sample,
            data_start,
            data_end: data_start.saturating_add(data_len),
            position: data_start,
        })
    }

    pub fn sample_rate(&self) -> i32 {
        self.sample_rate
    }

    pub fn channel_count(&self) -> i32 {
        self.channel_count
    }

    pub fn total_frames(&self) -> i64 {
        let frame = (self.bytes_per_sample as u64).saturating_mul(self.channel_count.max(1) as u64);
        if frame == 0 {
            return 0;
        }
        (self.data_end.saturating_sub(self.data_start) / frame) as i64
    }

    pub fn seek_frame(&mut self, frame: u64) -> Result<(), WavError> {
        let bytes = frame
            .saturating_mul(self.channel_count.max(1) as u64)
            .saturating_mul(self.bytes_per_sample as u64);
        let position = self.data_start.saturating_add(bytes).min(self.data_end);
        self.inner
            .seek(SeekFrom::Start(position))
            .map_err(WavError::Io)?;
        self.position = position;
        Ok(())
    }

    /// Reads interleaved samples into `dst`. Returns the number of samples written.
    pub fn read_samples(&mut self, dst: &mut [f32]) -> Result<usize, WavError> {
        let mut written = 0;
        while written < dst.len() && self.position + self.bytes_per_sample as u64 <= self.data_end {
            match self.read_one() {
                Ok(sample) => {
                    dst[written] = sample;
                    written += 1;
                }
                Err(WavError::Io(error)) if error.kind() == std::io::ErrorKind::UnexpectedEof => {
                    break;
                }
                Err(error) => return Err(error),
            }
        }
        Ok(written)
    }

    fn remaining_samples(&self) -> usize {
        let bytes = self.data_end.saturating_sub(self.position);
        (bytes / self.bytes_per_sample as u64) as usize
    }

    fn read_one(&mut self) -> Result<f32, WavError> {
        let mut buf = [0u8; 4];
        self.inner
            .read_exact(&mut buf[..self.bytes_per_sample])
            .map_err(WavError::Io)?;
        self.position += self.bytes_per_sample as u64;
        if self.bytes_per_sample == 2 {
            let value = i16::from_le_bytes([buf[0], buf[1]]);
            Ok(pcm16_to_sample(value))
        } else {
            Ok(f32::from_le_bytes(buf))
        }
    }
}

pub fn sample_to_pcm16(sample: f32) -> i16 {
    let scaled = sample.clamp(-1.0, 1.0) * 32767.0;
    (scaled as i32).clamp(-32768, 32767) as i16
}

pub fn pcm16_to_sample(value: i16) -> f32 {
    value as f32 / 32768.0
}

fn stream_len<R: Seek>(reader: &mut R) -> Result<u64, WavError> {
    let pos = reader.stream_position().map_err(WavError::Io)?;
    let end = reader.seek(SeekFrom::End(0)).map_err(WavError::Io)?;
    reader.seek(SeekFrom::Start(pos)).map_err(WavError::Io)?;
    Ok(end)
}

fn skip_rest<R: Read + Seek>(reader: &mut R, size: u64, file_end: u64) -> Result<(), WavError> {
    let pos = reader.stream_position().map_err(WavError::Io)?;
    let pad = size % 2;
    let target = pos.saturating_add(size).saturating_add(pad).min(file_end);
    reader.seek(SeekFrom::Start(target)).map_err(WavError::Io)?;
    Ok(())
}

fn write_u16(bytes: &mut [u8], offset: usize, value: u16) {
    bytes[offset] = value as u8;
    bytes[offset + 1] = (value >> 8) as u8;
}

fn write_u32(bytes: &mut [u8], offset: usize, value: u32) {
    bytes[offset] = value as u8;
    bytes[offset + 1] = (value >> 8) as u8;
    bytes[offset + 2] = (value >> 16) as u8;
    bytes[offset + 3] = (value >> 24) as u8;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pcm16_round_trip_matches_js_quantization() {
        let samples = [0.0f32, 0.5, -0.5, 1.0, -1.0, 0.25];
        let bytes = encode_wav16(&samples, 44100, 2).unwrap();
        let decoded = decode_wav(&bytes).unwrap();
        assert_eq!(decoded.sample_rate, 44100);
        assert_eq!(decoded.channel_count, 2);
        assert_eq!(decoded.samples.len(), samples.len());
        for (index, value) in samples.iter().enumerate() {
            assert!((decoded.samples[index] - value).abs() < 0.0001);
        }
    }

    #[test]
    fn streamed_read_matches_decode_wav() {
        let samples = [0.25f32, -0.25, 0.5, -0.5];
        let bytes = encode_wav16(&samples, 48000, 1).unwrap();
        let mut reader = WavReader::open(std::io::Cursor::new(bytes.clone())).unwrap();
        assert_eq!(reader.total_frames(), 4);
        let mut first = [0.0f32; 2];
        assert_eq!(reader.read_samples(&mut first).unwrap(), 2);
        reader.seek_frame(0).unwrap();
        let mut all = [0.0f32; 4];
        assert_eq!(reader.read_samples(&mut all).unwrap(), 4);
        assert_eq!(all.to_vec(), decode_wav(&bytes).unwrap().samples);
    }

    #[test]
    fn float_data_chunk_round_trips_bits() {
        let mut bytes = encode_wav16(&[0.0], 48000, 1).unwrap();
        bytes[20] = 3;
        bytes[34] = 32;
        bytes[40] = 4;
        bytes[41] = 0;
        bytes[42] = 0;
        bytes[43] = 0;
        bytes.truncate(44);
        bytes.extend_from_slice(&0.5f32.to_le_bytes());
        let riff_size = (bytes.len() - 8) as u32;
        bytes[4..8].copy_from_slice(&riff_size.to_le_bytes());
        let decoded = decode_wav(&bytes).unwrap();
        assert_eq!(decoded.samples, vec![0.5]);
    }

    #[test]
    fn rejects_a_non_wav_buffer() {
        assert!(matches!(
            decode_wav(b"not-a-wav-file"),
            Err(WavError::NotWav)
        ));
    }

    #[test]
    fn overstated_data_size_decodes_the_bytes_present() {
        let bytes = encode_wav16(&[0.5, -0.5], 44100, 1).unwrap();
        let mut inflated = bytes.clone();
        inflated[40] = 0xff;
        inflated[41] = 0xff;
        inflated[42] = 0xff;
        inflated[43] = 0xff;
        let decoded = decode_wav(&inflated).unwrap();
        assert_eq!(decoded.samples.len(), 2);
        assert!((decoded.samples[0] - 0.5).abs() < 0.0001);
    }

    #[test]
    fn twenty_four_bit_pcm_is_unsupported() {
        let mut bytes = encode_wav16(&[0.0], 44100, 1).unwrap();
        bytes[34] = 24;
        assert!(matches!(
            decode_wav(&bytes),
            Err(WavError::Unsupported {
                format: 1,
                bits: 24
            })
        ));
    }
}
