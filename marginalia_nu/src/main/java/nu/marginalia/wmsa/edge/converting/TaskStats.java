package nu.marginalia.wmsa.edge.converting;

public class TaskStats {
    private final long[] taskTimes;
    private int count = 0;
    private long total = 0;

    public TaskStats(int windowSize) {
        taskTimes = new long[windowSize];
    }

    public synchronized void observe(long time) {
        taskTimes[count++%taskTimes.length] = time;
        total += time;
    }

    public double avgTime() {
        long tts = 0;
        long tot;

        if (count < taskTimes.length) tot = count;
        else tot = taskTimes.length;

        for (int i = 0; i < tot; i++) tts += taskTimes[i];

        return (tot * 10_000L / tts)/10.;
    }

    public double totalTime() {
        return total;
    }

    public int getCount() {
        return count;
    }

}
