local user_balance_key = KEYS[1]
local total_price = tonumber(ARGV[#ARGV])

-- 1. Check user balance first
local current_balance = tonumber(redis.call('get', user_balance_key))
if not current_balance or current_balance < total_price then
    return -2 -- Error code: Insufficient balance
end

-- 2. Check stock for all requested products
local num_products = #KEYS - 1
local product_keys = {}
local quantities = {}

for i = 1, num_products do
    local p_key = KEYS[i + 1]
    local req_qty = tonumber(ARGV[i])
    local current_stock = tonumber(redis.call('get', p_key))
    
    if not current_stock or current_stock < req_qty then
        return -1 -- Error code: Out of stock
    end
    
    product_keys[i] = p_key
    quantities[i] = req_qty
end

-- 3. All checks passed: Deduct stock and balance on Redis RAM atomically
redis.call('incrbyfloat', user_balance_key, -total_price)
for i = 1, num_products do
    redis.call('decrby', product_keys[i], quantities[i])
end

return 1 -- Success code
