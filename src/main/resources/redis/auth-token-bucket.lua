local timestamp = redis.call('TIME')
local now = tonumber(timestamp[1]) * 1000 + math.floor(tonumber(timestamp[2]) / 1000)
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local rate = limit / window
local current = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
local last = tonumber(current[2]) or now
local tokens = math.min(limit, (tonumber(current[1]) or limit) + math.max(0, now - last) * rate)
if tokens < 1 then
  return math.ceil((1 - tokens) / rate)
end
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens - 1), 'updated', tostring(now))
redis.call('PEXPIRE', KEYS[1], window)
return 0
