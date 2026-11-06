//! Lock-free SPSC ring of trivially copyable messages.
//!
//! One slot stays empty so full and empty are distinct. Usable capacity is
//! `capacity - 1`. Item bytes are copied; Rust does not drop the `T`.

use std::alloc::{alloc_zeroed, dealloc, Layout};
use std::ptr;
use std::sync::atomic::{AtomicI32, Ordering};

pub struct Ring {
    data: *mut u8,
    layout: Layout,
    elem_size: usize,
    capacity: i32,
    read_pos: AtomicI32,
    write_pos: AtomicI32,
}

// Safety: one producer calls `push`. One consumer calls `pop`. The release
// store of each index publishes that side's slot bytes.
unsafe impl Send for Ring {}
unsafe impl Sync for Ring {}

impl Ring {
    pub fn new(capacity: i32, elem_size: i32, elem_align: i32) -> Option<Self> {
        if capacity < 2 || elem_size <= 0 || elem_align <= 0 {
            return None;
        }
        let elem_size = elem_size as usize;
        let align = elem_align as usize;
        let bytes = (capacity as usize).checked_mul(elem_size)?;
        let layout = Layout::from_size_align(bytes, align).ok()?;
        let data = unsafe { alloc_zeroed(layout) };
        if data.is_null() {
            return None;
        }
        Some(Self {
            data,
            layout,
            elem_size,
            capacity,
            read_pos: AtomicI32::new(0),
            write_pos: AtomicI32::new(0),
        })
    }

    pub fn push(&self, item: *const u8) -> bool {
        if item.is_null() {
            return false;
        }
        let current = self.write_pos.load(Ordering::Relaxed);
        let next = (current + 1) % self.capacity;
        if next == self.read_pos.load(Ordering::Acquire) {
            return false;
        }
        unsafe {
            ptr::copy_nonoverlapping(item, self.slot(current), self.elem_size);
        }
        self.write_pos.store(next, Ordering::Release);
        true
    }

    pub fn pop(&self, item: *mut u8) -> bool {
        if item.is_null() {
            return false;
        }
        let current = self.read_pos.load(Ordering::Relaxed);
        if current == self.write_pos.load(Ordering::Acquire) {
            return false;
        }
        unsafe {
            ptr::copy_nonoverlapping(self.slot(current), item, self.elem_size);
        }
        self.read_pos
            .store((current + 1) % self.capacity, Ordering::Release);
        true
    }

    pub fn is_empty(&self) -> bool {
        self.read_pos.load(Ordering::Acquire) == self.write_pos.load(Ordering::Acquire)
    }

    fn slot(&self, index: i32) -> *mut u8 {
        unsafe { self.data.add(index as usize * self.elem_size) }
    }
}

impl Drop for Ring {
    fn drop(&mut self) {
        if !self.data.is_null() {
            unsafe { dealloc(self.data, self.layout) }
        }
    }
}

#[no_mangle]
pub extern "C" fn klarinet_ring_create(
    capacity: i32,
    elem_size: i32,
    elem_align: i32,
) -> *mut Ring {
    match Ring::new(capacity, elem_size, elem_align) {
        Some(ring) => Box::into_raw(Box::new(ring)),
        None => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "C" fn klarinet_ring_destroy(ring: *mut Ring) {
    if ring.is_null() {
        return;
    }
    unsafe { drop(Box::from_raw(ring)) }
}

#[no_mangle]
pub extern "C" fn klarinet_ring_push(ring: *mut Ring, item: *const u8) -> i32 {
    if ring.is_null() {
        return 0;
    }
    i32::from(unsafe { (*ring).push(item) })
}

#[no_mangle]
pub extern "C" fn klarinet_ring_pop(ring: *mut Ring, item: *mut u8) -> i32 {
    if ring.is_null() {
        return 0;
    }
    i32::from(unsafe { (*ring).pop(item) })
}

#[no_mangle]
pub extern "C" fn klarinet_ring_is_empty(ring: *const Ring) -> i32 {
    if ring.is_null() {
        return 1;
    }
    i32::from(unsafe { (*ring).is_empty() })
}
