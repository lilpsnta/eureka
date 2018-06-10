/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.lease;

import com.netflix.eureka.registry.AbstractInstanceRegistry;

/**
 * Describes a time-based availability of a {@link T}. Purpose is to avoid
 * accumulation of instances in {@link AbstractInstanceRegistry} as result of ungraceful
 * shutdowns that is not uncommon in AWS environments.
 * 通过心跳来表达一个实例是活着的，防止一些实体在注册表中堆积，如果没有心跳的话，注册表中实例的状态和
 * 实例真实的状态是不一样的，就可能会出现调用到一具已经宕机的实例上
 *
 * If a lease elapses without renewals, it will eventually expire consequently
 * marking the associated {@link T} for immediate eviction - this is similar to
 * an explicit cancellation except that there is no communication between the
 * {@link T} and {@link LeaseManager}.
 *
 * 如果租约失效没有心跳了，他最终会过期标记相关的实例立即过期，这个直接摘除一个实例是一样的道理 。
 *
 * @author Karthik Ranganathan, Greg Kim
 */
public class Lease<T> {
    // 租约也有多种类，这个心跳是来注册的，还是摘除的，或正常的心跳
    enum Action {
        Register, Cancel, Renew
    };

    // 默认心跳感知是90秒？这个太长了，1分钟半才能感知的服务下架了？
    public static final int DEFAULT_DURATION_IN_SECS = 90;
    // 代表一个一租约关联的实例信息 Lease<InstanceInfo>
    private T holder;
    // 过期时间，哪个时间点过期的
    private long evictionTimestamp;
    private long registrationTimestamp;
    private long serviceUpTimestamp;
    // Make it volatile so that the expiration task would see this quicker
    private volatile long lastUpdateTimestamp;
    private long duration;

    public Lease(T r, int durationInSecs) {
        holder = r;
        registrationTimestamp = System.currentTimeMillis();
        lastUpdateTimestamp = registrationTimestamp;
        duration = (durationInSecs * 1000);

    }

    /**
     * Renew the lease, use renewal duration if it was specified by the
     * associated {@link T} during registration, otherwise default duration is
     * {@link #DEFAULT_DURATION_IN_SECS}.
     */
    public void renew() {
        // 最后更新时间是未来的一个时间？
        lastUpdateTimestamp = System.currentTimeMillis() + duration;

    }

    /**
     * Cancels the lease by updating the eviction time.
     * 更新过期日期就能摘除实例？
     *
     */
    public void cancel() {
        // 利用初始值来实现只能调用一次，后续调用无效
        if (evictionTimestamp <= 0) {
            evictionTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Mark the service as up. This will only take affect the first time called,
     * subsequent calls will be ignored.
     * 标记服务已经启动，仅限于第一次调用 ，后续调用将不起作用
     */
    public void serviceUp() {
        if (serviceUpTimestamp == 0) {
            serviceUpTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Set the leases service UP timestamp.
     */
    public void setServiceUpTimestamp(long serviceUpTimestamp) {
        this.serviceUpTimestamp = serviceUpTimestamp;
    }

    /**
     * Checks if the lease of a given {@link com.netflix.appinfo.InstanceInfo} has expired or not.
     * 判断租约是否过期，租约过期，说明失联了
     */
    public boolean isExpired() {
        return isExpired(0l);
    }

    /**
     * Checks if the lease of a given {@link com.netflix.appinfo.InstanceInfo} has expired or not.
     * Note that due to renew() doing the 'wrong" thing and setting lastUpdateTimestamp to +duration more than
     * what it should be, the expiry will actually be 2 * duration.
     * This is a minor bug and should only affect
     * instances that ungracefully shutdown.
     * renew() 将最后更新时间加了duration ,导到过期时间为duration的2倍
     * 这一个小bug 导致服务实例不会优雅停机
     * Due to possible wide ranging impact to existing usage, this will
     * not be fixed.
     * 鉴于不会大范围影响现在有用例，将不会被修复
     * @param additionalLeaseMs any additional lease time to add to the lease evaluation in ms.
     */
    public boolean isExpired(long additionalLeaseMs) {
        // 过期时间大于>0，说明初设置了过期时间，当前时间和最后一次更新时间已经超过规定的期限 duration
        // additionalLeaseMs 是什么鬼？ 额外加上的时间，单位是ms
        return (evictionTimestamp > 0 || System.currentTimeMillis() > (lastUpdateTimestamp + duration + additionalLeaseMs));
    }

    /**
     * Gets the milliseconds since epoch when the lease was registered.
     *
     * @return the milliseconds since epoch when the lease was registered.
     */
    public long getRegistrationTimestamp() {
        return registrationTimestamp;
    }

    /**
     * Gets the milliseconds since epoch when the lease was last renewed.
     * Note that the value returned here is actually not the last lease renewal time but the renewal + duration.
     *
     * @return the milliseconds since epoch when the lease was last renewed.
     */
    public long getLastRenewalTimestamp() {
        return lastUpdateTimestamp;
    }

    /**
     * Gets the milliseconds since epoch when the lease was evicted.
     *
     * @return the milliseconds since epoch when the lease was evicted.
     */
    public long getEvictionTimestamp() {
        return evictionTimestamp;
    }

    /**
     * Gets the milliseconds since epoch when the service for the lease was marked as up.
     *
     * @return the milliseconds since epoch when the service for the lease was marked as up.
     */
    public long getServiceUpTimestamp() {
        return serviceUpTimestamp;
    }

    /**
     * Returns the holder of the lease.
     */
    public T getHolder() {
        return holder;
    }

}
