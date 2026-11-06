//! Lock-free SPSC FIFO of interleaved `f32` samples.
//!
//! Capacity is rounded up to a power of two. Positions are free-running
//! counters. A slot is written only by the producer and read only by the
//! consumer, ordered by the release/acquire stores on those counters.

use std::alloc::{alloc_zeroed, dealloc, Layout};
use std::ptr;
use std::sync::atomic::{AtomicU32, Ordering};

pub struct AudioFifo {
    data: *mut f32,
    layout: Layout,
    capacity: u32,
    mask: u32,
    write_pos: AtomicU32,
    read_pos: AtomicU32,
}

// Safety: producer calls `write` only. Consumer calls `read` only.
// `available_*` load the counters. Slot bytes are published by the release
// store that advances that side's counter.
unsafe impl Send for AudioFifo {}
unsafe impl Sync for AudioFifo {}

impl AudioFifo {
    pub fn new(capacity_samples: i32) -> Option<Self> {
        let capacity = round_capacity(capacity_samples);
        let layout = Layout::array::<f32>(capacity as usize).ok()?;
        let data = unsafe { alloc_zeroed(layout) }.cast::<f32>();
        if data.is_null() {
            return None;
        }
        Some(Self {
            data,
            layout,
            capacity,
            mask: capacity - 1,
            write_pos: AtomicU32::new(0),
            read_pos: AtomicU32::new(0),
        })
    }

    pub fn write(&self, src: *const f32, count: i32) -> i32 {
        if src.is_null() || count <= 0 {
            return 0;
        }
        let w = self.write_pos.load(Ordering::Relaxed);
        let r = self.read_pos.load(Ordering::Acquire);
        let free = self.capacity.wrapping_sub(w.wrapping_sub(r));
        let n = (count as u32).min(free);
        self.copy_in(src, w, n);
        self.write_pos.store(w.wrapping_add(n), Ordering::Release);
        n as i32
    }

    pub fn read(&self, dst: *mut f32, count: i32) -> i32 {
        if dst.is_null() || count <= 0 {
            return 0;
        }
        let r = self.read_pos.load(Ordering::Relaxed);
        let w = self.write_pos.load(Ordering::Acquire);
        let filled = w.wrapping_sub(r);
        let n = (count as u32).min(filled);
        self.copy_out(dst, r, n);
        self.read_pos.store(r.wrapping_add(n), Ordering::Release);
        n as i32
    }

    pub fn available_to_read(&self) -> i32 {
        let w = self.write_pos.load(Ordering::Acquire);
        let r = self.read_pos.load(Ordering::Acquire);
        w.wrapping_sub(r) as i32
    }

    pub fn available_to_write(&self) -> i32 {
        self.capacity as i32 - self.available_to_read()
    }

    pub fn capacity(&self) -> i32 {
        self.capacity as i32
    }

    fn copy_in(&self, src: *const f32, start: u32, n: u32) {
        if n == 0 {
            return;
        }
        let index = (start & self.mask) as usize;
        let cap = self.capacity as usize;
        let count = n as usize;
        let mut first = count;
        if index + count > cap {
            first = cap - index;
        }
        unsafe {
            ptr::copy_nonoverlapping(src, self.data.add(index), first);
            if first < count {
                ptr::copy_nonoverlapping(src.add(first), self.data, count - first);
            }
        }
    }

    fn copy_out(&self, dst: *mut f32, start: u32, n: u32) {
        if n == 0 {
            return;
        }
        let index = (start & self.mask) as usize;
        let cap = self.capacity as usize;
        let count = n as usize;
        let mut first = count;
        if index + count > cap {
            first = cap - index;
        }
        unsafe {
            ptr::copy_nonoverlapping(self.data.add(index), dst, first);
            if first < count {
                ptr::copy_nonoverlapping(self.data, dst.add(first), count - first);
            }
        }
    }
}

impl Drop for AudioFifo {
    fn drop(&mut self) {
        if !self.data.is_null() {
            unsafe { dealloc(self.data.cast::<u8>(), self.layout) }
        }
    }
}

fn round_capacity(requested: i32) -> u32 {
    let mut cap: i32 = 1;
    while cap < requested {
        if cap > i32::MAX / 2 {
            break;
        }
        cap <<= 1;
    }
    cap as u32
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_create(capacity_samples: i32) -> *mut AudioFifo {
    match AudioFifo::new(capacity_samples) {
        Some(fifo) => Box::into_raw(Box::new(fifo)),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_destroy(fifo: *mut AudioFifo) {
    if fifo.is_null() {
        return;
    }
    unsafe { drop(Box::from_raw(fifo)) }
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_write(fifo: *mut AudioFifo, src: *const f32, count: i32) -> i32 {
    if fifo.is_null() {
        return 0;
    }
    unsafe { (*fifo).write(src, count) }
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_read(fifo: *mut AudioFifo, dst: *mut f32, count: i32) -> i32 {
    if fifo.is_null() {
        return 0;
    }
    unsafe { (*fifo).read(dst, count) }
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_available_to_read(fifo: *const AudioFifo) -> i32 {
    if fifo.is_null() {
        return 0;
    }
    unsafe { (*fifo).available_to_read() }
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_available_to_write(fifo: *const AudioFifo) -> i32 {
    if fifo.is_null() {
        return 0;
    }
    unsafe { (*fifo).available_to_write() }
}

#[no_mangle]
pub extern "C" fn klarinet_fifo_capacity(fifo: *const AudioFifo) -> i32 {
    if fifo.is_null() {
        return 0;
    }
    unsafe { (*fifo).capacity() }
}
