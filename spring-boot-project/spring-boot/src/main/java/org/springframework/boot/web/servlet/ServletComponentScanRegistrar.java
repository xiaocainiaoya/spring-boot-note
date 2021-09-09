/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.servlet;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * {@link ImportBeanDefinitionRegistrar} used by
 * {@link ServletComponentScan @ServletComponentScan}.
 *
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 */
class ServletComponentScanRegistrar implements ImportBeanDefinitionRegistrar {

	private static final String BEAN_NAME = "servletComponentRegisteringPostProcessor";

	/**
	 * ImportBeanDefinitionRegistrar的扩展接口, 用于解析bean到beanDefinition
	 * 	这里用于将servletComponentRegisteringPostProcessor后置处理器添加到springbean容器中
	 *
	 * @Author: xiaocainiaoya
	 * @Date: 2021/08/26 14:31:54
	 * @param importingClassMetadata
	 * @param registry
	 * @return:
	 **/
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		Set<String> packagesToScan = getPackagesToScan(importingClassMetadata);
		if (registry.containsBeanDefinition(BEAN_NAME)) {
			updatePostProcessor(registry, packagesToScan);
		}else {
			addPostProcessor(registry, packagesToScan);
		}
	}

	/**
	 * 获取已存在的后置处理器,并获取构造参数中的扫描包路径, 与传入参数中新的扫描包路径合并。
	 *
	 * @Author: xiaocainiaoya
	 * @Date: 2021/08/26 14:41:39
	 * @param registry
	 * @param packagesToScan
	 * @return:
	 **/
	private void updatePostProcessor(BeanDefinitionRegistry registry, Set<String> packagesToScan) {
		BeanDefinition definition = registry.getBeanDefinition(BEAN_NAME);
		ValueHolder constructorArguments = definition.getConstructorArgumentValues().getGenericArgumentValue(Set.class);
		@SuppressWarnings("unchecked")
		Set<String> mergedPackages = (Set<String>) constructorArguments.getValue();
		mergedPackages.addAll(packagesToScan);
		constructorArguments.setValue(mergedPackages);
	}

	/**
	 * 添加ServletComponentRegisteringPostProcessor这个后置处理器
	 *
	 * @Author: xiaocainiaoya
	 * @Date: 2021/08/26 14:40:21
	 * @param registry
	 * @param packagesToScan
	 * @return:
	 **/
	private void addPostProcessor(BeanDefinitionRegistry registry, Set<String> packagesToScan) {
		GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
		beanDefinition.setBeanClass(ServletComponentRegisteringPostProcessor.class);
		beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(packagesToScan);
		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(BEAN_NAME, beanDefinition);
	}

	/**
	 * 先获取ServletComponentScan注解中的basePackages路径
	 * 	若为空, 则表示仅获取当前类的全限定类名
	 *
	 * @Author: xiaocainiaoya
	 * @Date: 2021/08/26 14:38:03
	 * @param metadata
	 * @return:
	 **/
	private Set<String> getPackagesToScan(AnnotationMetadata metadata) {
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(ServletComponentScan.class.getName()));
		String[] basePackages = attributes.getStringArray("basePackages");
		Class<?>[] basePackageClasses = attributes.getClassArray("basePackageClasses");
		Set<String> packagesToScan = new LinkedHashSet<>(Arrays.asList(basePackages));
		for (Class<?> basePackageClass : basePackageClasses) {
			packagesToScan.add(ClassUtils.getPackageName(basePackageClass));
		}
		if (packagesToScan.isEmpty()) {
			packagesToScan.add(ClassUtils.getPackageName(metadata.getClassName()));
		}
		return packagesToScan;
	}

}
