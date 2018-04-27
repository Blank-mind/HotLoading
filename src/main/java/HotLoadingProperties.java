/*
 * @(#)HotLoadingProperties.java
 *
 * Create Version: 1.0.0
 * Author: Liu Shan
 * Create Date: 2017-09-22
 *
 * Copyright (c) 2017 Liushan. All Right Reserved.
 */


import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Map.Entry;


/**
 * <p>
 * 这个类是负责加载properties文件的类，提供热加载功能。热加载功能，使用Timer的Deamon线程实现。
 * </p>
 * <p>
 * 默认不打开热加载，如果需要打开请调用openHotLoad()，如果需要关闭请调用closeHotLoad()。<br/>
 * 在Spring环境中，声明bean时，可以用init-method="openHotLoad" destory-method="closeHotLoad".
 * </p>
 * <pre>
 * 目前仅支持char、Character、byte、Byte、int、Integer、short、Short、long、Long、float、Float、double、Double、
 * boolean、Boolean、String、Class、File、URL、
 * java.sql.Date(自定义格式，yyyy-MM-dd，yyyyMMdd)、
 * java.sql.Time(自定义格式，HH:mm:ss.SSS，HHmmss.SSS，HHmmssSSS，HH:mm:ss，HHmmss)、
 * java.sql.Timestamp(自定义格式，yyyy-MM-dd HH:mm:ss.SSS，yyyy-MM-ddTHH:mm:ss.SSS，yyyyMMddHHmmss.SSS，
 *                    yyyyMMddHHmmssSSS，yyyy-MM-dd HH:mm:ss，yyyy-MM-ddTHH:mm:ss，yyyyMMddHHmmss)、
 * java.time.LocalDate(自定义格式，yyyy-MM-dd，yyyyMMdd)、
 * java.time.LocalTime(自定义格式，HH:mm:ss.SSS，HHmmss.SSS，HHmmssSSS，HH:mm:ss，HHmmss)、
 * java.time.LocalDateTime(自定义格式，yyyy-MM-dd HH:mm:ss.SSS，yyyy-MM-ddTHH:mm:ss.SSS，yyyyMMddHHmmss.SSS，
 *                         yyyyMMddHHmmssSSS，yyyy-MM-dd HH:mm:ss，yyyy-MM-ddTHH:mm:ss，yyyyMMddHHmmss)。
 * </pre>
 *
 * @author Liu Shan
 * @version 1.0.0   2017-09-25
 */
public class HotLoadingProperties {

    private final Map<Class<?>, Converter> converters = new HashMap<Class<?>, Converter>();

    {
        converters.put(String.class, new StringConverter());
        converters.put(char.class, new CharacterConverter());
        converters.put(Character.class, new CharacterConverter());
        converters.put(byte.class, new ByteConverter());
        converters.put(Byte.class, new ByteConverter());
        converters.put(int.class, new IntegerConverter());
        converters.put(Integer.class, new IntegerConverter());
        converters.put(short.class, new ShortConverter());
        converters.put(Short.class, new ShortConverter());
        converters.put(long.class, new LongConverter());
        converters.put(Long.class, new LongConverter());
        converters.put(float.class, new FloatConverter());
        converters.put(Float.class, new FloatConverter());
        converters.put(double.class, new DoubleConverter());
        converters.put(Double.class, new DoubleConverter());
        converters.put(boolean.class, new BooleanConverter());
        converters.put(Boolean.class, new BooleanConverter());
        converters.put(Date.class, new DateConverter());
        converters.put(Time.class, new TimeConverter());
        converters.put(Timestamp.class, new TimestampConverter());
        converters.put(LocalDate.class, new LocalDateConverter());
        converters.put(LocalTime.class, new LocalTimeConverter());
        converters.put(LocalDateTime.class, new LocalDateTimeConverter());
        converters.put(Class.class, new ClassConverter());
        converters.put(File.class, new FileConverter());
        converters.put(URL.class, new URLConverter());
    }

    private File file;
    private long interval;

    private Timer checkerTimer;
    private long lastModified = -1L;
    private LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();


    /**
     * 构造方法。
     * 默认不打开热加载。
     *
     * @param file     配置文件的文件对象
     * @param interval 热加载间隔时间，单位：毫秒
     * @throws NullPointerException     当file参数为null时抛出。
     * @throws IllegalArgumentException 当file不存在或不是文件、interval不是大于0时抛出。
     */
    public HotLoadingProperties(File file, long interval) {
        this(file, interval, false);
    }

    /**
     * 参数最全的构造方法。
     *
     * @param file       配置文件的文件对象
     * @param interval   热加载间隔时间，单位：毫秒
     * @param hotloading 是否打开热加载
     * @throws NullPointerException     当file参数为null时抛出。
     * @throws IllegalArgumentException 当file不存在或不是文件、interval不是大于0时抛出。
     */
    public HotLoadingProperties(File file, long interval, boolean hotloading) {
        if (file == null) throw new NullPointerException("File is null!");
        if (!file.isFile()) throw new IllegalArgumentException("File[" + this.file + "] is not a file!");
        if (interval <= 0) throw new IllegalArgumentException("Interval must be greater than 0!");

        this.file = file;
        this.interval = interval;
        this.load();
        if (hotloading) this.openHotLoad();
    }

    private long getLastModified() {
        return (this.file.isFile()) ? this.file.lastModified() : 0L;
    }

    private boolean needReload() {
        long lastModified = this.getLastModified();
        return ((lastModified > 0L) && (this.lastModified != lastModified));
    }

    private void load() {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(this.file));
            Properties props = new LinkedProperties();
            props.load(is);

            LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
            Set<String> keySet = props.stringPropertyNames();
            for (String key : keySet) map.put(key, props.getProperty(key));

            synchronized (this) {
                this.map = map;
                this.lastModified = this.getLastModified();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("File[" + this.file + "] can't be loaded!", e);
        } finally {
            try {
                is.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * 打开热加载.
     */
    public synchronized void openHotLoad() {
        if (this.checkerTimer == null) {
            this.checkerTimer = new Timer(true);
            this.checkerTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (needReload()) load();
                }
            }, this.interval, this.interval);
        }
    }

    /**
     * 关闭热加载。
     */
    public synchronized void closeHotLoad() {
        if (this.checkerTimer != null) {
            this.checkerTimer.cancel();
            this.checkerTimer = null;
        }
    }

    /**
     * 判断是否打开了热加载。
     *
     * @return 是否打开了热加载
     */
    public boolean isHotLoad() {
        return (this.checkerTimer != null);
    }

    /**
     * 返回此映射中的键-值映射关系数。如果该映射包含的元素大于 Integer.MAX_VALUE，则返回 Integer.MAX_VALUE。
     *
     * @return 此映射中的键-值映射关系数
     */
    public int size() {
        return this.size();
    }

    /**
     * 如果此映射未包含键-值映射关系，则返回 true。
     *
     * @return 如果此映射未包含键-值映射关系，则返回 true
     */
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    /**
     * 如果此映射包含指定键的映射关系，则返回 true。
     * 更确切地讲，当且仅当此映射包含针对满足 (key==null ? k==null : key.equals(k)) 的键 k 的映射关系时，返回 true。（最多只能有一个这样的映射关系）。
     *
     * @param key 测试是否存在于此映射中的键
     * @return 如果此映射包含指定键的映射关系，则返回 true
     */
    public boolean containsKey(String key) {
        return this.map.containsKey(key);
    }

    /**
     * 如果此映射将一个或多个键映射到指定值，则返回 true。
     * 更确切地讲，当且仅当此映射至少包含一个对满足 (value==null ? v==null : value.equals(v)) 的值 v 的映射关系时，返回 true。
     *
     * @param value 测试是否存在于此映射中的值
     * @return 如果此映射将一个或多个键映射到指定值，则返回 true
     */
    public boolean containsValue(String value) {
        return this.map.containsValue(value);
    }

    /**
     * 返回此映射中包含的键的 Set 视图。该 set 受映射支持，所以对映射的更改可在此 set 中反映出来，反之亦然。
     * 如果对该 set 进行迭代的同时修改了映射（通过迭代器自己的 remove 操作除外），则迭代结果是不确定的。
     * set 支持元素移除，通过 Iterator.remove、Set.remove、removeAll、retainAll 和 clear 操作可从映射中移除相应的映射关系。
     * 它不支持 add 或 addAll 操作。
     *
     * @return 此映射中包含的键的 set 视图
     */
    public Set<String> keySet() {
        return this.map.keySet();
    }

    /**
     * 返回此映射中包含的值的 Collection 视图。该 collection 受映射支持，所以对映射的更改可在此 collection 中反映出来，反之亦然。
     * 如果对该 collection 进行迭代的同时修改了映射（通过迭代器自己的 remove 操作除外），则迭代结果是不确定的。
     * collection 支持元素移除，通过 Iterator.remove、Collection.remove、removeAll、retainAll 和 clear 操作可从映射中移除相应的映射关系。
     * 它不支持 add 或 addAll 操作。
     *
     * @return 此映射中包含的值的 collection 视图
     */
    public Collection<String> values() {
        return this.map.values();
    }

    /**
     * 返回此映射中包含的映射关系的 Set 视图。该 set 受映射支持，所以对映射的更改可在此 set 中反映出来，反之亦然。
     * 如果对该 set 进行迭代的同时修改了映射（通过迭代器自己的 remove 操作，或者通过对迭代器返回的映射项执行 setValue 操作除外），则迭代结果是不确定的。
     * set 支持元素移除，通过 Iterator.remove、Set.remove、removeAll、retainAll 和 clear 操作可从映射中移除相应的映射关系。
     * 它不支持 add 或 addAll 操作。
     *
     * @return 此映射中包含的映射关系的 set 视图
     */
    public Set<Entry<String, String>> entrySet() {
        return this.map.entrySet();
    }

    /**
     * 获得字符串配置项。
     *
     * @param key 配置关键字
     * @return 字符串配置项
     */
    public String get(String key) {
        return this.get(String.class, key, null, null);
    }

    /**
     * 获得字符串配置项。
     *
     * @param key          配置关键字
     * @param defaultValue 默认值
     * @return 字符串配置项
     */
    public String get(String key, String defaultValue) {
        return this.get(String.class, key, null, defaultValue);
    }

    /**
     * 获得支持的数据类型配置项。
     *
     * @param clazz 要返回类型的Class对象
     * @param key   配置关键字
     * @return 支持的数据类型配置项
     */
    public <T> T get(Class<T> clazz, String key) {
        return this.get(clazz, key, null, null);
    }

    /**
     * 获得支持的数据类型配置项。
     *
     * @param clazz        要返回类型的Class对象
     * @param key          配置关键字
     * @param defaultValue 默认值
     * @return 支持的数据类型配置项
     */
    public <T> T get(Class<T> clazz, String key, T defaultValue) {
        return this.get(clazz, key, null, defaultValue);
    }

    /**
     * 获得支持的数据类型配置项。
     *
     * @param clazz   要返回类型的Class对象
     * @param key     配置关键字
     * @param pattern 转换模式字符串
     * @return 支持的数据类型配置项
     */
    public <T> T get(Class<T> clazz, String key, String pattern) {
        return this.get(clazz, key, pattern, null);
    }

    /**
     * 获得支持的数据类型配置项。
     *
     * @param clazz        要返回类型的Class对象
     * @param key          配置关键字
     * @param pattern      转换模式字符串
     * @param defaultValue 默认值
     * @return 支持的数据类型配置项
     */
    public <T> T get(Class<T> clazz, String key, String pattern, T defaultValue) {
        if (clazz == null) throw new NullPointerException("Class is null!");
        if (key == null) throw new NullPointerException("Key is null!");

        T result = this.convert(clazz, map.get(key), pattern);
        return (result == null) ? defaultValue : result;
    }

    /**
     * 获得字符串配置项列表。
     *
     * @param key   配置关键字
     * @param regex 定界正则表达式
     * @return 字符串配置项列表
     */
    public List<String> getList(String key, String regex) {
        return this.getList(String.class, key, regex, null, null);
    }

    /**
     * 获得字符串配置项列表。
     *
     * @param key          配置关键字
     * @param regex        定界正则表达式
     * @param defaultValue 当多值中某个值为null时的默认值
     * @return 字符串配置项列表
     */
    public List<String> getList(String key, String regex, String defaultValue) {
        return this.getList(String.class, key, regex, null, defaultValue);
    }

    /**
     * 获得支持的数据类型多值配置项列表。
     *
     * @param clazz   要返回类型的Class对象
     * @param key     配置关键字
     * @param regex   定界正则表达式
     * @param pattern 转换模式字符串
     * @return 支持的数据类型多值配置项列表
     */
    public <T> List<T> getList(Class<T> clazz, String key, String regex, String pattern) {
        return this.getList(clazz, key, regex, pattern, null);
    }

    /**
     * 获得支持的数据类型多值配置项列表。
     *
     * @param clazz        要返回类型的Class对象
     * @param key          配置关键字
     * @param regex        定界正则表达式
     * @param pattern      转换模式字符串
     * @param defaultValue 当多值中某个值为null时的默认值
     * @return 支持的数据类型多值配置项列表
     */
    public <T> List<T> getList(Class<T> clazz, String key, String regex, String pattern, T defaultValue) {
        if (clazz == null) throw new NullPointerException("Class is null!");
        if (key == null) throw new NullPointerException("Key is null!");

        String stringValue = this.get(String.class, key, pattern, null);
        if (stringValue == null) return new ArrayList<T>(0);

        String[] sArray = stringValue.split(regex);
        List<T> result = new ArrayList<T>(sArray.length);
        for (String s : sArray) {
            T value = this.convert(clazz, s, pattern);
            result.add((value != null) ? value : defaultValue);
        }
        return result;
    }

    /**
     * 获得分组的组名列表。
     *
     * @param prefix 键的固定前缀。
     * @param suffix 键的固定前缀。
     * @return 分组的组名列表
     */
    public List<String> getGroupNames(String prefix, String suffix) {
        if (prefix == null) throw new NullPointerException("Prefix is null!");
        if (suffix == null) throw new NullPointerException("Suffix is null!");

        List<String> result = new LinkedList<String>();
        Set<String> keySet = map.keySet();
        for (String key : keySet) {
            if (key.startsWith(prefix) && key.endsWith(suffix)) {
                int prefixIndex = key.indexOf(prefix);
                int beginIndex = (prefixIndex == -1) ? 0 : prefixIndex + prefix.length();
                int suffixIndex = key.lastIndexOf(suffix);
                int endIndex = (suffixIndex == -1) ? key.length() : suffixIndex;
                result.add(key.substring(beginIndex, endIndex));
            }
        }
        return result;
    }

    /**
     * 通过配置值找出所有配置项的key。
     *
     * @param value 配置的值
     * @return 找出的所有配置项key
     */
    public List<String> getKeysByValue(String value) {
        if (value == null) throw new NullPointerException("Value is null!");

        List<String> result = new LinkedList<String>();
        Set<Entry<String, String>> entrySet = map.entrySet();
        for (Entry<String, String> entry : entrySet) {
            if (value.equals(entry.getValue())) result.add(entry.getKey());
        }
        return result;
    }

    /**
     * 将字符串转换为支持的数据类型的工具方法。
     *
     * @param clazz   要返回类型的Class对象
     * @param s       要转换的字符串
     * @param pattern 转换模式字符串
     * @return 支持的数据类型对象
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Class<T> clazz, String s, String pattern) {
        if (clazz == null) throw new NullPointerException("Class is null!");

        Converter converter = this.converters.get(clazz);
        if (converter == null) throw new UnsupportedOperationException("Can't convert string to " + clazz.getName());

        return (s == null) ? null : (T) converter.convert(s, pattern);
    }

    /**
     * 将转换器与指定的类关联。
     *
     * @param clazz     Class对象
     * @param converter 转换器对象
     * @return 以前与此类关联的转换器对象
     */
    public Converter putConverter(Class<?> clazz, Converter converter) {
        if (clazz == null) throw new NullPointerException("Class is null!");

        return this.converters.put(clazz, converter);
    }

    /**
     * 如果存在一个类和转换器的映射关系，则将其从此映射中移除。
     *
     * @param clazz Class对象
     * @return 以前与此类关联的转换器对象
     */
    public Converter removeConverter(Class<?> clazz) {
        if (clazz == null) throw new NullPointerException("Class is null!");

        return this.converters.remove(clazz);
    }

    /**
     * 从此映射中移除所有类-转换器映射关系。
     */
    public void clearConverters() {
        this.converters.clear();
    }

    /**
     * 返回此映射中的类-转换器映射关系数。
     *
     * @return 此映射中的类-转换器映射关系数
     */
    public int countConverters() {
        return this.converters.size();
    }


    /**
     * 这个类是有顺序的Properties。
     *
     * @author Liu Shan
     * @version 1.0.0   2013-03-18
     */
    private static class LinkedProperties extends Properties {

        private static final long serialVersionUID = -3618175179127741465L;

        private final LinkedHashSet<Object> keys = new LinkedHashSet<Object>();


        public Enumeration<Object> keys() {
            return Collections.enumeration(this.keys);
        }

        public Object put(Object key, Object value) {
            this.keys.add(key);
            return super.put(key, value);
        }

        public Set<Object> keySet() {
            return this.keys;
        }

        public Set<String> stringPropertyNames() {
            Set<String> set = new LinkedHashSet<String>();
            for (Object key : this.keys) set.add((String) key);
            return set;
        }
    }


    /**
     * 这个接口是数据类型转换器的接口。
     *
     * @author Liu Shan
     * @version 1.0.0   2017-09-22
     */
    public static interface Converter {
        public Object convert(String s, String pattern);
    }


    private static class StringConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            return s;
        }
    }

    private static class CharacterConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Character result = null;
            if (s.length() != 0) result = new Character(s.charAt(0));
            return result;
        }
    }

    private static class ByteConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Byte result = null;
            try {
                result = Byte.valueOf(s.trim());
            } catch (NumberFormatException nfe) {
            }
            return result;
        }
    }

    private static class IntegerConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Integer result = null;
            try {
                result = Integer.valueOf(s.trim());
            } catch (NumberFormatException nfe) {
            }
            return result;
        }
    }

    private static class ShortConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Short result = null;
            try {
                result = Short.valueOf(s.trim());
            } catch (NumberFormatException nfe) {
            }
            return result;
        }
    }

    private static class LongConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Long result = null;
            try {
                result = Long.valueOf(s.trim());
            } catch (NumberFormatException nfe) {
            }
            return result;
        }
    }

    private static class FloatConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Float result = null;
            try {
                result = Float.valueOf(s.trim());
            } catch (NumberFormatException nfe) {
            }
            return result;
        }
    }

    private static class DoubleConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Double result = null;
            try {
                result = Double.valueOf(s.trim());
            } catch (NumberFormatException nfe) {
            }
            return result;
        }
    }

    private static class BooleanConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            //Boolean result = (T) Boolean.valueOf(s.trim());
            Boolean result = null;
            s = s.trim();
            if ("true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s)
                    || "y".equalsIgnoreCase(s) || "1".equalsIgnoreCase(s)) {
                result = Boolean.TRUE;
            } else if ("false".equalsIgnoreCase(s) || "no".equalsIgnoreCase(s)
                    || "n".equalsIgnoreCase(s) || "0".equalsIgnoreCase(s)) {
                result = Boolean.FALSE;
            }
            return result;
        }
    }

    private static class DateConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Date result = null;
            s = s.trim();
            if ((pattern != null) && (pattern.length() > 0)) {
                try {
                    result = new Date((new SimpleDateFormat(pattern)).parse(s).getTime());
                } catch (ParseException e) {
                }
            } else {
                try {
                    result = new Date((new SimpleDateFormat("yyyy-MM-dd")).parse(s).getTime());
                } catch (ParseException e1) {
                    try {
                        result = new Date((new SimpleDateFormat("yyyyMMdd")).parse(s).getTime());
                    } catch (ParseException e2) {
                    }
                }
            }
            return result;
        }
    }

    private static class TimeConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Time result = null;
            s = s.trim();
            if ((pattern != null) && (pattern.length() > 0)) {
                try {
                    result = new Time((new SimpleDateFormat(pattern)).parse(s).getTime());
                } catch (ParseException e) {
                }
            } else {
                try {
                    result = new Time((new SimpleDateFormat("HH:mm:ss.SSS")).parse(s).getTime());
                } catch (ParseException e1) {
                    try {
                        result = new Time((new SimpleDateFormat("HHmmss.SSS")).parse(s).getTime());
                    } catch (ParseException e2) {
                        try {
                            result = new Time((new SimpleDateFormat("HHmmssSSS")).parse(s).getTime());
                        } catch (ParseException e3) {
                            try {
                                result = new Time((new SimpleDateFormat("HH:mm:ss")).parse(s).getTime());
                            } catch (ParseException e4) {
                                try {
                                    result = new Time((new SimpleDateFormat("HHmmss")).parse(s).getTime());
                                } catch (ParseException e5) {
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
    }

    private static class TimestampConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Timestamp result = null;
            s = s.trim();
            if ((pattern != null) && (pattern.length() > 0)) {
                try {
                    result = new Timestamp((new SimpleDateFormat(pattern)).parse(s).getTime());
                } catch (ParseException e) {
                }
            } else {
                try {
                    result = new Timestamp((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")).parse(s).getTime());
                } catch (ParseException e1) {
                    try {
                        result = new Timestamp((new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSS")).parse(s).getTime());
                    } catch (ParseException e2) {
                        try {
                            result = new Timestamp((new SimpleDateFormat("yyyyMMddHHmmss.SSS")).parse(s).getTime());
                        } catch (ParseException e3) {
                            try {
                                result = new Timestamp((new SimpleDateFormat("yyyyMMddHHmmssSSS")).parse(s).getTime());
                            } catch (ParseException e4) {
                                try {
                                    result = new Timestamp((new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).parse(s).getTime());
                                } catch (ParseException e5) {
                                    try {
                                        result = new Timestamp((new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss")).parse(s).getTime());
                                    } catch (ParseException e6) {
                                        try {
                                            result = new Timestamp((new SimpleDateFormat("yyyyMMddHHmmss")).parse(s).getTime());
                                        } catch (ParseException e7) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
    }

    private static class LocalDateConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            LocalDate result = null;
            s = s.trim();
            if ((pattern != null) && (pattern.length() > 0)) {
                try {
                    result = LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern));
                } catch (DateTimeParseException e) {
                }
            } else {
                try {
                    result = LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (DateTimeParseException e1) {
                    try {
                        result = LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    } catch (DateTimeParseException e2) {
                    }
                }
            }
            return result;
        }
    }

    private static class LocalTimeConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            LocalTime result = null;
            s = s.trim();
            if ((pattern != null) && (pattern.length() > 0)) {
                try {
                    result = LocalTime.parse(s, DateTimeFormatter.ofPattern(pattern));
                } catch (DateTimeParseException e) {
                }
            } else {
                try {
                    result = LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                } catch (DateTimeParseException e1) {
                    try {
                        result = LocalTime.parse(s, DateTimeFormatter.ofPattern("HHmmss.SSS"));
                    } catch (DateTimeParseException e2) {
                        try {
                            result = LocalTime.parse(s, DateTimeFormatter.ofPattern("HHmmssSSS"));
                        } catch (DateTimeParseException e3) {
                            try {
                                result = LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm:ss"));
                            } catch (DateTimeParseException e4) {
                                try {
                                    result = LocalTime.parse(s, DateTimeFormatter.ofPattern("HHmmss"));
                                } catch (DateTimeParseException e5) {
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
    }

    private static class LocalDateTimeConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            LocalDateTime result = null;
            s = s.trim();
            if ((pattern != null) && (pattern.length() > 0)) {
                try {
                    result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern));
                } catch (DateTimeParseException e) {
                }
            } else {
                try {
                    result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                } catch (DateTimeParseException e1) {
                    try {
                        result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss.SSS"));
                    } catch (DateTimeParseException e2) {
                        try {
                            result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSS"));
                        } catch (DateTimeParseException e3) {
                            try {
                                result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                            } catch (DateTimeParseException e4) {
                                try {
                                    result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                } catch (DateTimeParseException e5) {
                                    try {
                                        result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss"));
                                    } catch (DateTimeParseException e6) {
                                        try {
                                            result = LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                                        } catch (DateTimeParseException e7) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }
    }

    private static class ClassConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            Class<?> result = null;
            try {
                result = Class.forName(s.trim());
            } catch (ClassNotFoundException e) {
            }
            return result;
        }
    }

    private static class FileConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            File result = null;
            try {
                result = new File(s);
                result = result.getCanonicalFile();
            } catch (IOException e) {
                result = null;
            }
            return result;
        }
    }

    private static class URLConverter implements Converter {
        @Override
        public Object convert(String s, String pattern) {
            URL result = null;
            try {
                result = new URL(s.trim());
            } catch (MalformedURLException e) {
            }
            return result;
        }
    }
}