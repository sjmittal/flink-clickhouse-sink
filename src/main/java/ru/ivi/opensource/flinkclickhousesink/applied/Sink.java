package ru.ivi.opensource.flinkclickhousesink.applied;

public class Sink<T> implements AutoCloseable {
    private final ClickHouseSinkBuffer<T> clickHouseSinkBuffer;

    public Sink(ClickHouseSinkBuffer<T> buffer) {
        this.clickHouseSinkBuffer = buffer;
    }


    public void put(T message) {
        clickHouseSinkBuffer.put(message);
    }

    @Override
    public void close() {
        if (clickHouseSinkBuffer != null) {
            clickHouseSinkBuffer.close();
        }
    }
}
