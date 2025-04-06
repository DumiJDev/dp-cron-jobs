# DPCron

**DPCron** is a lightweight Java library for scheduling background tasks using Unix-style cron expressions. It provides
a clean, minimal API with multithreaded execution support and enhanced cron expression features (like steps: `*/5`,
`1-10/2`, etc.).

## 📦 Features

- Cron-based task scheduling (5 fields)
- Supports ranges, multiple values, and step values
- Asynchronous execution using a thread pool
- Functional interfaces (`Runnable`, `Consumer<JobContext>`)
- Job control: enable/disable jobs
- Fetch next/last run time

## 🚀 Installation

Include the source code in your Java project. No external dependencies except [SLF4J](http://www.slf4j.org/) for
logging.

```xml
<!-- Example if you package it as a jar and use Maven -->
<dependency>
  <groupId>io.github.dumijdev</groupId>
  <artifactId>dpcronjobs</artifactId>
  <version>1.0.0</version>
</dependency>
```

## 🛠️ Basic Usage

```java
import io.github.dumijdev.dpcronjobs.DPCron;

public class Main {
  public static void main(String[] args) {
    DPCron cron = new DPCron();

    cron.schedule("*/1 * * * *", () -> {
      System.out.println("Running every minute: " + java.time.LocalDateTime.now());
    });
  }
}
```

### Using `Consumer<JobContext>`

```java
cron.schedule("0 9 * * *",context ->{
    System.out.

println("Running at 9 AM: "+context.getExecutionTime());
    });
```

## 🧩 Cron Expression Syntax

The expression consists of **5 space-separated fields**:

```text
MINUTE (0–59)   HOUR (0–23)   DAY_OF_MONTH (1–31)   MONTH (1–12)   DAY_OF_WEEK (0–6, where 0 = Sunday)
```

Examples:

- `*/5 * * * *` — Every 5 minutes
- `0 0 * * 0` — Every Sunday at midnight
- `15 14 1 * *` — At 14:15 on the 1st of every month

### Supported Features:

- `*` — any value
- `a,b,c` — multiple values
- `a-b` — ranges
- `*/n` or `a-b/n` — step values

## 📚 Public API

### `DPCron`

```java
DPCron cron = new DPCron();                   // Uses available processors
DPCron cron = new DPCron(int threadPoolSize); // Manual thread pool size
```

- `schedule(String cronExpr, Runnable task)` — Schedule a simple task
- `schedule(String cronExpr, Consumer<JobContext>)` — Schedule with execution context
- `getJobs()` — Returns a list of registered jobs
- `stop()` — Cancels all jobs and shuts down the scheduler

### `CronJob`

- `enable()` / `disable()` — Enable or disable a job
- `isEnabled()` — Check if a job is currently active
- `getLastRunTime()` / `getNextRunTime()` — Access run timestamps

### `JobContext`

- `getExecutionTime()` — Returns the execution timestamp for the job

## ✅ Advanced Example

```java
cron.schedule("0 */2 * * *",() ->{
    // Runs every 2 hours
```

## ⚠️ Notes

- Tasks run in a separate thread pool. Long-running jobs **won’t block** the scheduler.
- Tasks should be **thread-safe** if they share any state.

## 🧪 Testing

Use logs or print the `context.getExecutionTime()` to verify scheduled behavior.

## 📄 License

This project is licensed under the MIT License. See the `LICENSE` file for details.

---

Made with 💡 by [DumijDev](https://github.com/dumijdev)

```