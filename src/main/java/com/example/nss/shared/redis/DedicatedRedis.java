package com.example.nss.shared.redis;

import java.net.URI;
import java.time.Duration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Keeps optional Redis workloads off the session connection and connection factory. */
public final class DedicatedRedis implements AutoCloseable {
  private static final String TLS_SCHEME = "rediss";
  private final LettuceConnectionFactory connection;
  private final StringRedisTemplate redisTemplate;

  public DedicatedRedis(String url, Duration timeout) {
    URI uri = URI.create(url);
    if (!"redis".equals(uri.getScheme()) && !TLS_SCHEME.equals(uri.getScheme())) {
      throw new IllegalArgumentException("A URL Redis deve usar redis ou rediss");
    }
    RedisStandaloneConfiguration server =
        new RedisStandaloneConfiguration(uri.getHost(), uri.getPort() == -1 ? 6379 : uri.getPort());
    String userInfo = uri.getUserInfo();
    if (userInfo != null) {
      String[] parts = userInfo.split(":", 2);
      if (parts.length == 2 && !parts[0].isEmpty()) server.setUsername(parts[0]);
      server.setPassword(RedisPassword.of(parts[parts.length - 1]));
    }
    if (uri.getPath() != null && uri.getPath().length() > 1) {
      server.setDatabase(Integer.parseInt(uri.getPath().substring(1)));
    }
    var config = LettuceClientConfiguration.builder().commandTimeout(timeout);
    if (TLS_SCHEME.equals(uri.getScheme())) config.useSsl();
    connection = new LettuceConnectionFactory(server, config.build());
    connection.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connection);
  }

  public StringRedisTemplate template() {
    return redisTemplate;
  }

  @Override
  public void close() {
    connection.destroy();
  }
}
