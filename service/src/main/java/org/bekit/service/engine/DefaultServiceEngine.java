/* 
 * 作者：钟勋 (e-mail:zhongxunking@163.com)
 */

/*
 * 修订记录:
 * @author 钟勋 2016-12-16 01:14 创建
 */
package org.bekit.service.engine;

import lombok.AllArgsConstructor;
import org.bekit.event.EventPublisher;
import org.bekit.service.ServiceEngine;
import org.bekit.service.event.ServiceApplyEvent;
import org.bekit.service.event.ServiceExceptionEvent;
import org.bekit.service.event.ServiceFinishEvent;
import org.bekit.service.service.ServiceExecutor;
import org.bekit.service.service.ServiceRegistrar;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务引擎默认实现类
 */
@AllArgsConstructor
public class DefaultServiceEngine implements ServiceEngine {
    // 服务注册器
    private final ServiceRegistrar serviceRegistrar;
    // 服务事件发布器
    private final EventPublisher eventPublisher;

    @Override
    public <O, R> R execute(String service, O order) {
        return execute(service, order, null);
    }

    @Override
    public <O, R> R execute(String service, O order, Map<Object, Object> attachment) {
        // 获取服务执行器
        ServiceExecutor serviceExecutor = serviceRegistrar.get(service);
        Assert.notNull(serviceExecutor, String.format("服务[%s]不存在", service));
        // 校验order
        checkOrder(order, serviceExecutor);
        // 构建服务上下文
        ServiceContext<O, R> context = new ServiceContext<>(order, (R) newResult(serviceExecutor), reviseAttachment(attachment));
        // 执行服务
        executeService(serviceExecutor, context);

        return context.getResult();
    }

    // 校验入参order
    private void checkOrder(Object order, ServiceExecutor serviceExecutor) {
        Assert.notNull(order, "order不能为null");
        if (!serviceExecutor.getOrderClass().isAssignableFrom(order.getClass())) {
            throw new IllegalArgumentException(String.format("入参order的类型[%s]和服务[%s]期望的类型不匹配", order.getClass(), serviceExecutor.getServiceName()));
        }
    }

    // 创建result
    private Object newResult(ServiceExecutor serviceExecutor) {
        return BeanUtils.instantiate(serviceExecutor.getResultClass());
    }

    // 修正附件
    private Map<Object, Object> reviseAttachment(Map<Object, Object> attachment) {
        return attachment != null ? attachment : new HashMap<>();
    }

    // 执行服务
    private void executeService(ServiceExecutor serviceExecutor, ServiceContext context) {
        try {
            // 发布服务申请事件
            eventPublisher.publish(new ServiceApplyEvent(serviceExecutor.getServiceName(), context));
            // 执行服务
            serviceExecutor.execute(context);
        } catch (Throwable e) {
            // 发布服务异常事件
            eventPublisher.publish(new ServiceExceptionEvent(serviceExecutor.getServiceName(), e, context));
        } finally {
            // 发布服务结束事件
            eventPublisher.publish(new ServiceFinishEvent(serviceExecutor.getServiceName(), context));
        }
    }
}
