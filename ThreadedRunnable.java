public abstract class ThreadedRunnable implements Runnable {

    /**Properties*/
    private Thread thread;

    /**
     * @return the thread that is wrapped within this class
     */
    public Thread getThread() {
        if (this.thread != null) {
            return this.thread;
        }
        this.thread = new Thread(this);
        return this.thread;
    }

    /**
     * Start the thread, creating it if it was not initialized
     */
    public void start() {
        Thread t = this.getThread();
        if (t.getState() != Thread.State.NEW) return;
        t.start();
    }

    /**
     * Wait for the thread to finish before continuing with
     * execution
     */
    public void join() throws InterruptedException {
        Thread t = this.getThread();
        if (t.getState() == Thread.State.NEW) t.start();
        t.join();
    }
}
