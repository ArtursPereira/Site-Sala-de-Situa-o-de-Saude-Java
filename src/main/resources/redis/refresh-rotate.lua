local current = redis.call('HMGET', KEYS[1], 'userId', 'expiresAt', 'revokedAt', 'generation', 'family', 'credential')
local generation = redis.call('GET', KEYS[3]) or '0'
local family = current[5] or KEYS[1]
local familyKey = ARGV[7] .. family
if current[1] ~= ARGV[2] or current[2] ~= ARGV[6] or (current[3] and current[3] ~= '')
    or (current[4] or '0') ~= generation or redis.call('PTTL', KEYS[1]) <= 0
    or current[6] ~= ARGV[8] or redis.call('HGET', familyKey, 'revoked') == '1'
    or redis.call('EXISTS', KEYS[2]) ~= 0 then return 0 end
redis.call('HSET', KEYS[2], 'id', ARGV[1], 'userId', ARGV[2], 'createdAt', ARGV[3],
  'expiresAt', ARGV[4], 'generation', generation, 'family', family, 'credential', current[6], 'revokedAt', '', 'replacedBy', '')
redis.call('PEXPIRE', KEYS[2], ARGV[5])
redis.call('HSETNX', familyKey, 'revoked', '0')
if redis.call('PTTL', familyKey) < tonumber(ARGV[5]) then redis.call('PEXPIRE', familyKey, ARGV[5]) end
redis.call('HSET', KEYS[1], 'revokedAt', ARGV[3], 'replacedBy', ARGV[1])
return 1
