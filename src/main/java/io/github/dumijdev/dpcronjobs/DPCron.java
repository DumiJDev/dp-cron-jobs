package io.github.dumijdev.dpcronjobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A lightweight Java library for scheduling tasks using cron expressions.
 */
public class DPCron {
  private static final Logger logger = LoggerFactory.getLogger(DPCron.class);
  private final ScheduledExecutorService scheduler;
  private final ExecutorService taskExecutor;
  private final List<CronJob> jobs;

  /**
   * Create a new JavaCron instance with the specified number of threads.
   *
   * @param threadPoolSize The size of the thread pool for executing jobs
   */
  public DPCron(int threadPoolSize) {
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
    this.taskExecutor = Executors.newFixedThreadPool(threadPoolSize);
    this.jobs = new ArrayList<>();
  }

  /**
   * Create a new JavaCron instance with default thread pool size.
   */
  public DPCron() {
    this(Runtime.getRuntime().availableProcessors());
  }

  /**
   * Schedule a job with the specified cron expression and task.
   *
   * @param cronExpression The cron expression for scheduling
   * @param task           The task to execute
   * @return A CronJob instance representing the scheduled job
   */
  public CronJob schedule(String cronExpression, Runnable task) {
    CronExpression cron = new CronExpression(cronExpression);
    CronJob job = new CronJob(cron, task);
    jobs.add(job);
    scheduleNext(job);
    return job;
  }

  /**
   * Schedule a job with the specified cron expression and task consumer.
   *
   * @param cronExpression The cron expression for scheduling
   * @param consumer       The consumer to execute with job info
   * @return A CronJob instance representing the scheduled job
   */
  public CronJob schedule(String cronExpression, Consumer<JobContext> consumer) {
    return schedule(cronExpression, () -> {
      JobContext context = new JobContext(LocalDateTime.now());
      consumer.accept(context);
    });
  }

  /**
   * Schedule the next execution of a job.
   *
   * @param job The job to schedule
   */
  private void scheduleNext(CronJob job) {
    if (!job.isEnabled()) {
      return;
    }

    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
    ZonedDateTime nextRun = job.getExpression().getNextValidTime(now);

    if (nextRun != null) {
      long delay = ChronoUnit.MILLIS.between(now, nextRun);

      job.setNextRunTime(nextRun.toLocalDateTime());
      job.setScheduledFuture(scheduler.schedule(() -> {
        if (job.isEnabled()) {
          executeJob(job);
          scheduleNext(job);
        }
      }, delay, TimeUnit.MILLISECONDS));
    }
  }

  /**
   * Execute a job in the thread pool.
   *
   * @param job The job to execute
   */
  private void executeJob(CronJob job) {
    taskExecutor.submit(() -> {
      try {
        job.setLastRunTime(LocalDateTime.now());
        job.getTask().run();
      } catch (Exception e) {
        logger.error("Error executing job: {}", e.getMessage(), e);
      }
    });
  }

  /**
   * Stop all scheduled jobs.
   */
  public void stop() {
    jobs.forEach(CronJob::disable);
    scheduler.shutdown();
    taskExecutor.shutdown();
  }

  /**
   * Get all scheduled jobs.
   *
   * @return A list of all scheduled jobs
   */
  public List<CronJob> getJobs() {
    return new ArrayList<>(jobs);
  }

  /**
   * Simple context object for jobs with consumer API.
   */
  public static class JobContext {
    private final LocalDateTime executionTime;

    public JobContext(LocalDateTime executionTime) {
      this.executionTime = executionTime;
    }

    public LocalDateTime getExecutionTime() {
      return executionTime;
    }
  }

  /**
   * Represents a scheduled cron job.
   */
  public static class CronJob {
    private final CronExpression expression;
    private final Runnable task;
    private boolean enabled;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private ScheduledFuture<?> scheduledFuture;

    /**
     * Create a new CronJob with the specified expression and task.
     *
     * @param expression The cron expression
     * @param task       The task to execute
     */
    public CronJob(CronExpression expression, Runnable task) {
      this.expression = expression;
      this.task = task;
      this.enabled = true;
    }

    /**
     * Get the cron expression for this job.
     *
     * @return The cron expression
     */
    public CronExpression getExpression() {
      return expression;
    }

    /**
     * Get the task for this job.
     *
     * @return The task
     */
    public Runnable getTask() {
      return task;
    }

    /**
     * Check if this job is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Enable this job.
     */
    public void enable() {
      this.enabled = true;
    }

    /**
     * Disable this job.
     */
    public void disable() {
      this.enabled = false;
      if (scheduledFuture != null) {
        scheduledFuture.cancel(false);
      }
    }

    /**
     * Get the last run time of this job.
     *
     * @return The last run time, or null if never run
     */
    public LocalDateTime getLastRunTime() {
      return lastRunTime;
    }

    /**
     * Set the last run time of this job.
     *
     * @param lastRunTime The last run time
     */
    void setLastRunTime(LocalDateTime lastRunTime) {
      this.lastRunTime = lastRunTime;
    }

    /**
     * Get the next scheduled run time of this job.
     *
     * @return The next run time, or null if not scheduled
     */
    public LocalDateTime getNextRunTime() {
      return nextRunTime;
    }

    /**
     * Set the next run time of this job.
     *
     * @param nextRunTime The next run time
     */
    void setNextRunTime(LocalDateTime nextRunTime) {
      this.nextRunTime = nextRunTime;
    }

    /**
     * Set the scheduled future for this job.
     *
     * @param scheduledFuture The scheduled future
     */
    void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
      if (this.scheduledFuture != null) {
        this.scheduledFuture.cancel(false);
      }
      this.scheduledFuture = scheduledFuture;
    }
  }

  /**
   * Parser and evaluator for Unix cron expressions.
   * Supports standard cron format with five fields: minute, hour, day of month, month, day of week.
   * Now with enhanced support for step values (any/2, 1-5/2, etc.)
   */
  public static class CronExpression {
    private static final Pattern CRON_PATTERN = Pattern.compile(
        "^([*\\d,\\-/]+)\\s+" + // minutes
            "([*\\d,\\-/]+)\\s+" + // hours
            "([*\\d,\\-/]+)\\s+" + // days of month
            "([*\\d,\\-/]+)\\s+" + // months
            "([*\\d,\\-/]+)$"      // days of week
    );

    private final List<Integer> minutes = new ArrayList<>();
    private final List<Integer> hours = new ArrayList<>();
    private final List<Integer> daysOfMonth = new ArrayList<>();
    private final List<Integer> months = new ArrayList<>();
    private final List<Integer> daysOfWeek = new ArrayList<>();
    private final String expression;

    /**
     * Create a new CronExpression from the given expression string.
     *
     * @param expression The cron expression to parse
     */
    public CronExpression(String expression) {
      this.expression = expression;
      parse(expression);
    }

    private static List<Integer> getIntegers(int min, int max, String[] stepParts) {
      List<Integer> rangeValues = new ArrayList<>();
      String rangeExpr = stepParts[0];

      if (rangeExpr.equals("*")) {
        // Full range
        IntStream.rangeClosed(min, max).forEach(rangeValues::add);

      } else if (rangeExpr.contains("-")) {
        // Specific range with bounds
        String[] rangeBounds = rangeExpr.split("-");
        int start = Integer.parseInt(rangeBounds[0]);
        int end = Integer.parseInt(rangeBounds[1]);

        IntStream.rangeClosed(start, end).filter(i -> i >= min && i <= max).forEach(rangeValues::add);
      } else {
        // Single value start for step
        int start = Integer.parseInt(rangeExpr);

        IntStream.rangeClosed(start, max).filter(i -> i >= min).forEach(rangeValues::add);
      }
      return rangeValues;
    }

    /**
     * Parse the given cron expression.
     *
     * @param expression The cron expression to parse
     * @throws IllegalArgumentException if the expression is invalid
     */
    private void parse(String expression) {
      Matcher matcher = CRON_PATTERN.matcher(expression);
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Invalid cron expression: " + expression);
      }

      parseField(matcher.group(1), minutes, 0, 59);
      parseField(matcher.group(2), hours, 0, 23);
      parseField(matcher.group(3), daysOfMonth, 1, 31);
      parseField(matcher.group(4), months, 1, 12);
      parseField(matcher.group(5), daysOfWeek, 0, 6); // 0 = Sunday, 6 = Saturday
    }

    /**
     * Parse a field of the cron expression with enhanced support for step values.
     *
     * @param field  The field to parse
     * @param target The list to add parsed values to
     * @param min    The minimum valid value
     * @param max    The maximum valid value
     */
    private void parseField(String field, List<Integer> target, int min, int max) {
      // Split by commas and process each part
      for (String part : field.split(",")) {
        if (part.contains("/")) {
          // Handle step values
          String[] stepParts = part.split("/");
          int stepSize = Integer.parseInt(stepParts[1]);

          if (stepSize <= 0) {
            throw new IllegalArgumentException("Step size must be positive: " + part);
          }

          // Define range for stepping
          List<Integer> rangeValues = getIntegers(min, max, stepParts);

          // Apply step to the range
          for (int i = 0; i < rangeValues.size(); i += stepSize) {
            int value = rangeValues.get(i);
            if (!target.contains(value)) {
              target.add(value);
            }
          }
        } else if (part.contains("-")) {
          // Handle ranges
          String[] range = part.split("-");
          int start = Integer.parseInt(range[0]);
          int end = Integer.parseInt(range[1]);

          IntStream.rangeClosed(start, end).filter(i -> i >= min && i <= max && !target.contains(i)).forEach(target::add);
        } else if (part.equals("*")) {
          // Handle wildcards
          IntStream.rangeClosed(min, max).filter(value -> !target.contains(value)).forEach(target::add);
        } else {
          // Handle single values
          int value = Integer.parseInt(part);
          if (value >= min && value <= max && !target.contains(value)) {
            target.add(value);
          }
        }
      }

      target.sort(Integer::compareTo);
    }

    /**
     * Get the next valid time after the given time.
     *
     * @param after The time to start from
     * @return The next valid time, or null if none exists
     */
    public ZonedDateTime getNextValidTime(ZonedDateTime after) {
      ZonedDateTime candidate = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);

      int iterations = 0;
      int maxIterations = 1000000; // ~2 years when checking minute by minute

      while (iterations < maxIterations) {
        if (matches(candidate)) {
          return candidate;
        }
        candidate = candidate.plusMinutes(1);
        iterations++;
      }

      return null;
    }

    /**
     * Check if the given time matches this cron expression.
     *
     * @param time The time to check
     * @return true if the time matches, false otherwise
     */
    public boolean matches(ZonedDateTime time) {
      int minute = time.getMinute();
      int hour = time.getHour();
      int dayOfMonth = time.getDayOfMonth();
      int month = time.getMonthValue();
      int dayOfWeek = time.getDayOfWeek().getValue() % 7;

      return minutes.contains(minute) &&
          hours.contains(hour) &&
          months.contains(month) &&
          (daysOfMonth.contains(dayOfMonth) || daysOfWeek.contains(dayOfWeek));
    }

    /**
     * Get the cron expression string.
     *
     * @return The cron expression string
     */
    public String getExpression() {
      return expression;
    }
  }
}