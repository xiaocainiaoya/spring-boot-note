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

package org.springframework.boot.logging;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.SystemPropertyUtils;

/**
 * 次顶层抽象接口，空实现了beforeInitalize(), 实现了初始化initialize接口
 *
 * Abstract base class for {@link LoggingSystem} implementations.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @since 1.0.0
 */
public abstract class AbstractLoggingSystem extends LoggingSystem {

	protected static final Comparator<LoggerConfiguration> CONFIGURATION_COMPARATOR = new LoggerConfigurationComparator(ROOT_LOGGER_NAME);

	private final ClassLoader classLoader;

	public AbstractLoggingSystem(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void beforeInitialize() {
	}

	@Override
	public void initialize(LoggingInitializationContext initializationContext, String configLocation, LogFile logFile) {
		// 如果传递了日志配置文件，调用initializeWithSpecificConfig方法，使用指定的文件
		if (StringUtils.hasLength(configLocation)) {
			initializeWithSpecificConfig(initializationContext, configLocation, logFile);
			return;
		}
		// 没有传递日志配置文件的话调用initializeWithConventions方法，使用约定俗成的方式
		initializeWithConventions(initializationContext, logFile);
	}

	/**
	 * 根据指定的配置进行初始化
	 *
	 * @Author: xiaocainiaoya
	 * @Date: 2021/09/03 10:04:03
	 * @param initializationContext
	 * @param configLocation
	 * @param logFile
	 * @return:
	 **/
	private void initializeWithSpecificConfig(LoggingInitializationContext initializationContext, String configLocation, LogFile logFile) {
		configLocation = SystemPropertyUtils.resolvePlaceholders(configLocation);
		loadConfiguration(initializationContext, configLocation, logFile);
	}

	/**
	 * 根据约定俗成的方式进行初始化日志系统
	 * 	1. 根据模板接口getStandardConfigLocations()由子类实现获取到文件数组。遍历获取类路径下第一个存在的资源。classpath:a/b/c.xxx
	 * 	2. 若不存在, 则在文件数组后每项后添加-spring后再进行遍历获取类路径下第一个存在的资源  classpath:a/b/c-spring.xxx
	 * 	3. 若不存在, 调用loadConfiguration()模板方法
	 * 	4. 若不存在, 调用loadDefaults()模板方法
	 *
	 * @Author: xiaocainiaoya
	 * @Date: 2021/09/03 10:04:24
	 * @param initializationContext
	 * @param logFile
	 * @return:
	 **/
	private void initializeWithConventions(LoggingInitializationContext initializationContext, LogFile logFile) {
		// 获取自初始化的日志配置文件，该方法会使用getStandardConfigLocations抽象方法得到的文件数组
		// 然后进行遍历，如果文件存在，返回对应的文件目录。注意这里的文件指的是classpath下的文件
		String config = getSelfInitializationConfig();
		// 如果找到对应的日志配置文件并且logFile为null(logFile为null表示只有console会输出)
		if (config != null && logFile == null) {
			// self initialization has occurred, reinitialize in case of property changes
			// 调用reinitialize方法重新初始化
			// 默认的reinitialize方法不做任何处理，logback,log4j和log4j2覆盖了这个方法，会进行处理
			reinitialize(initializationContext);
			return;
		}
		// 如果没有找到对应的日志配置文件
		if (config == null) {
			// 调用getSpringInitializationConfig方法获取日志配置文件
			// 该方法与getSelfInitializationConfig方法的区别在于getStandardConfigLocations方法得到的文件数组内部遍历的逻辑
			// getSelfInitializationConfig方法直接遍历并判断classpath下是否存在对应的文件
			// getSpringInitializationConfig方法遍历后判断的文件名会在后缀前加上 "-spring" 字符串
			// 比如查找logback.xml文件，getSelfInitializationConfig会直接查找classpath下是否存在logback.xml文件，而getSpringInitializationConfig方法会判断classpath下是否存在logback-spring.xml文件
			config = getSpringInitializationConfig();
		}
		// 如果没有找到对应的日志配置文件
		if (config != null) {
			// 调用loadConfiguration抽象方法，让子类实现
			loadConfiguration(initializationContext, config, logFile);
			return;
		}
		// 还是没找到日志配置文件的话，调用loadDefaults抽象方法加载，让子类实现
		loadDefaults(initializationContext, logFile);
	}

	/**
	 * Return any self initialization config that has been applied. By default this method
	 * checks {@link #getStandardConfigLocations()} and assumes that any file that exists
	 * will have been applied.
	 * @return the self initialization config or {@code null}
	 */
	protected String getSelfInitializationConfig() {
		return findConfig(getStandardConfigLocations());
	}

	/**
	 * Return any spring specific initialization config that should be applied. By default
	 * this method checks {@link #getSpringConfigLocations()}.
	 * @return the spring initialization config or {@code null}
	 */
	protected String getSpringInitializationConfig() {
		return findConfig(getSpringConfigLocations());
	}

	private String findConfig(String[] locations) {
		for (String location : locations) {
			ClassPathResource resource = new ClassPathResource(location, this.classLoader);
			if (resource.exists()) {
				return "classpath:" + location;
			}
		}
		return null;
	}

	/**
	 * Return the standard config locations for this system.
	 * @return the standard config locations
	 * @see #getSelfInitializationConfig()
	 */
	protected abstract String[] getStandardConfigLocations();

	/**
	 * Return the spring config locations for this system. By default this method returns
	 * a set of locations based on {@link #getStandardConfigLocations()}.
	 * @return the spring config locations
	 * @see #getSpringInitializationConfig()
	 */
	protected String[] getSpringConfigLocations() {
		String[] locations = getStandardConfigLocations();
		for (int i = 0; i < locations.length; i++) {
			// 获取对应文件的后缀 a/b/c.txt -> txt
			String extension = StringUtils.getFilenameExtension(locations[i]);
			// 拼接-spring a/b/c-spring.txt
			locations[i] = locations[i].substring(0, locations[i].length() - extension.length() - 1) + "-spring." + extension;
		}
		return locations;
	}

	/**
	 * Load sensible defaults for the logging system.
	 * @param initializationContext the logging initialization context
	 * @param logFile the file to load or {@code null} if no log file is to be written
	 */
	protected abstract void loadDefaults(LoggingInitializationContext initializationContext, LogFile logFile);

	/**
	 * Load a specific configuration.
	 * @param initializationContext the logging initialization context
	 * @param location the location of the configuration to load (never {@code null})
	 * @param logFile the file to load or {@code null} if no log file is to be written
	 */
	protected abstract void loadConfiguration(LoggingInitializationContext initializationContext, String location, LogFile logFile);

	/**
	 * Reinitialize the logging system if required. Called when
	 * {@link #getSelfInitializationConfig()} is used and the log file hasn't changed. May
	 * be used to reload configuration (for example to pick up additional System
	 * properties).
	 * @param initializationContext the logging initialization context
	 */
	protected void reinitialize(LoggingInitializationContext initializationContext) {
	}

	protected final ClassLoader getClassLoader() {
		return this.classLoader;
	}

	protected final String getPackagedConfigFile(String fileName) {
		String defaultPath = ClassUtils.getPackageName(getClass());
		defaultPath = defaultPath.replace('.', '/');
		defaultPath = defaultPath + "/" + fileName;
		defaultPath = "classpath:" + defaultPath;
		return defaultPath;
	}

	protected final void applySystemProperties(Environment environment, LogFile logFile) {
		new LoggingSystemProperties(environment).apply(logFile);
	}

	/**
	 * Maintains a mapping between native levels and {@link LogLevel}.
	 *
	 * @param <T> the native level type
	 */
	protected static class LogLevels<T> {

		private final Map<LogLevel, T> systemToNative;

		private final Map<T, LogLevel> nativeToSystem;

		public LogLevels() {
			this.systemToNative = new EnumMap<>(LogLevel.class);
			this.nativeToSystem = new HashMap<>();
		}

		public void map(LogLevel system, T nativeLevel) {
			this.systemToNative.putIfAbsent(system, nativeLevel);
			this.nativeToSystem.putIfAbsent(nativeLevel, system);
		}

		public LogLevel convertNativeToSystem(T level) {
			return this.nativeToSystem.get(level);
		}

		public T convertSystemToNative(LogLevel level) {
			return this.systemToNative.get(level);
		}

		public Set<LogLevel> getSupported() {
			return new LinkedHashSet<>(this.nativeToSystem.values());
		}

	}

}
