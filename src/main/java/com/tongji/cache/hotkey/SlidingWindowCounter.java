package com.tongji.cache.hotkey;


import java.util.concurrent.atomic.AtomicLong;

public class SlidingWindowCounter {
//    环形时间片数组
    private  final AtomicLong[] slices;
//    时间片数量
    private final int windowSize;
//    时间片持续时间
    private final int timeMillisPerSlice;
//    数组长度，时间片数量*2，环形数组相当于两个相同的数组首尾相接
    private final int timeSliceSize;
//    热点阈值
    private final long threshold;
//    开始时间
    private long beginTimestamp;
//    最后一次加入时间
    private long lastAddTimestamp;
    public SlidingWindowCounter(int durationSeconds, int threshold) {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be > 0");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be > 0");
        }
//        窗口计数时间小于5秒，窗口分5片(200ms=1s/5)
        if (durationSeconds <= 5) {
            this.windowSize = 5;
            this.timeMillisPerSlice = durationSeconds * 200;
        } else {
            this.windowSize = 10;
            this.timeMillisPerSlice = durationSeconds * 100;
        }

        this.threshold = threshold;
        this.timeSliceSize = windowSize * 2;
        this.slices = new AtomicLong[timeSliceSize];

        for (int i = 0; i < timeSliceSize; i++) {
            this.slices[i] = new AtomicLong(0);
        }

        long now = System.currentTimeMillis();
        this.beginTimestamp = now;
        this.lastAddTimestamp = now;
    }

//    synchronized 保证线程安全
    public synchronized boolean addCount(long count){
        long now = System.currentTimeMillis();
//        获取现在需要写在哪一个时间片
        int index = locationIndex();
//         清除index之后长度为windowSize的数组值，只有index之前长度为windowSize的数组值有效
        clearExpiredSlices(index);
        long sum = slices[index].addAndGet(count);
        for(int i=1;i<windowSize;i++){
            sum+=slices[(index-i+timeSliceSize)%timeSliceSize].get();
        }
        lastAddTimestamp = System.currentTimeMillis();
        return sum>=threshold;
    }

    private int locationIndex(){
        long now = System.currentTimeMillis();
        if(now-beginTimestamp>=(long)timeMillisPerSlice*windowSize){
            reset(now);
        }
        int index = (int) (((now - beginTimestamp) / timeMillisPerSlice) % timeSliceSize);
        return Math.max(index,0);
    }
    public void clearExpiredSlices(int index){
        for(int i=1;i<=windowSize;i++){
            slices[(index+i)%timeSliceSize].set(0);
        }
    }

    private void reset(long now){
        for(int i=0;i<timeSliceSize;i++){
            slices[i].set(0);
        }
        beginTimestamp = now;
        lastAddTimestamp = now;
    }
}
