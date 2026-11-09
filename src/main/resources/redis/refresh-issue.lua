local generation = redis.call('GET', KEYS[2]) or '0'
if generation ~= ARGV[6] or redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
redis.call('HSET', KEYS[1], 'id', ARGV[1], 'userId', ARGV[2], 'createdAt', ARGV[3],
  'expiresAt', ARGV[4], 'generation', generation, 'family', ARGV[7], 'credential', ARGV[8], 'revokedAt', '', 'replacedBy', '')
redis.call('PEXPIRE', KEYS[1], ARGV[5])
return 1
