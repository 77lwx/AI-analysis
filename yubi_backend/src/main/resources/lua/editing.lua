-- 获取键
local key = KEYS[1]

-- 获取当前值
local currentValue = redis.call('GET', key)

-- 如果值为空，初始化为 0 并返回 true
if currentValue == false or currentValue == nil  then
    redis.call('SET', key, 1)
    return true
end

-- 将值转换为数字
currentValue = tonumber(currentValue)

if currentValue <=0  then
    redis.call('SET', key, 1)
    return true
end

-- 判断值是否大于等于 3
if currentValue >= 3 then
    return false
else
    -- 对值加 1
    redis.call('INCR', key)
    return true
end