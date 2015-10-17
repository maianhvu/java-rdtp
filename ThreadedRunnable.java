public abstract class ThreadedRunnable implements Runnable {

  /**
   * Properties
   */
  private Thread thread;

  /**
   * @return A Thread that wraps this runnable
   */
  public Thread getThread() {
    if (this.thread == null) {
      this.thread = new Thread(this);
    }
    return this.thread;
  }

  /**
   * Start the thread
   */
  public void start() {
    Thread t = this.getThread();
    if (t.getState() == Thread.State.NEW) t.start();
  }
}
