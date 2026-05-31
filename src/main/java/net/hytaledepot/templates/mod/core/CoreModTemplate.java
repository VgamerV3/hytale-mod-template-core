package net.hytaledepot.templates.mod.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoreModTemplate {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final AtomicBoolean demoFlagEnabled = new AtomicBoolean(false);
  private final AtomicLong errorCount = new AtomicLong();
  private final Map<String, String> moduleHealth = new ConcurrentHashMap<>();
  private volatile Path dataDirectory;

  public void onInitialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    moduleHealth.clear();
    moduleHealth.put("commands", "healthy");
    moduleHealth.put("heartbeat", "healthy");
    moduleHealth.put("storage", "healthy");
    moduleHealth.put("integrations", "healthy");
  }

  public void onShutdown() {
    moduleHealth.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();
    if (tick % 120 == 0) {
      moduleHealth.putIfAbsent("heartbeat", "healthy");
    }
  }

  public String runAction(String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = toggleFlag(demoFlagEnabled);
      return "[CoreMod] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[CoreMod] " + diagnostics(normalizedSender, heartbeatTicks);
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[CoreMod] " + domainResult;
    }

    return "[CoreMod] unknown action='" + normalizedAction + "' (try: info, toggle, sample, module-scan, mark-unhealthy, mark-healthy)";
  }

  public String diagnostics(String sender, long heartbeatTicks) {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "sender=" + sender
        + ", heartbeatTicks=" + heartbeatTicks
        + ", demoFlag=" + demoFlagEnabled.get()
        + ", ops=" + operationCount()
        + ", lastAction=" + lastActionBySender.getOrDefault(sender, "none")
        + ", errors=" + errorCount.get()
        + ", modules=" + moduleHealth.size() + ", healthy=" + moduleHealth.values().stream().filter("healthy"::equals).count() + ", degraded=" + (moduleHealth.size() - moduleHealth.values().stream().filter("healthy"::equals).count()) + ", dataDirectory=" + directory;
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public void incrementErrorCount() {
    errorCount.incrementAndGet();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "module-scan".equals(action)) {
      long healthy = moduleHealth.values().stream().filter("healthy"::equals).count();
      return "module scan complete, healthy=" + healthy + "/" + moduleHealth.size();
    }
    if ("mark-unhealthy".equals(action)) {
      moduleHealth.put("integrations", "degraded");
      return "integrations marked degraded";
    }
    if ("mark-healthy".equals(action)) {
      moduleHealth.replaceAll((key, value) -> "healthy");
      return "all modules marked healthy";
    }
    return null;
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }

  private static boolean toggleFlag(AtomicBoolean flag) {
    while (true) {
      boolean current = flag.get();
      boolean next = !current;
      if (flag.compareAndSet(current, next)) {
        return next;
      }
    }
  }
}
