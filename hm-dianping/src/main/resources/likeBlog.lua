-- ===== 原实现（已注释）：基于 Set，只能记录"是否点赞"，无法按时间排序 =====
--local key = KEYS[1]
--local userId = ARGV[1]
--local isMember = redis.call('SISMEMBER', key, userId)
--if isMember == 1 then
--    redis.call('SREM', key, userId)
--    return 0   -- 已点赞 -> 取消
--else
--    redis.call('SADD', key, userId)
--    return 1   -- 未点赞 -> 点赞
--end

-- ===== 新实现：基于 ZSet，score 存点赞时间戳，可做点赞排行榜 =====
local key = KEYS[1]
local userId = ARGV[1]
local score = ARGV[2]          -- 点赞时间戳（毫秒）
local isMember = redis.call('ZSCORE', key, userId)
if isMember then
    redis.call('ZREM', key, userId)
    return 0   -- 已点赞 -> 取消
else
    redis.call('ZADD', key, score, userId)
    return 1   -- 未点赞 -> 点赞
end
