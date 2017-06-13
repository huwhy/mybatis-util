package cn.huwhy.ibatis;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.lang.UsesJava7;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import cn.huwhy.interfaces.EnumValue;

@Intercepts({
        @Signature(method = "handleResultSets", type = ResultSetHandler.class, args = {
                Statement.class})})
public class ResultTypeInterceptor implements Interceptor {

    private static Map<Class, Map<String, PropertyDescriptor>> cacheFields = new HashMap<>();

    public Object intercept(Invocation invocation) throws Throwable {
        Statement st = (Statement) invocation.getArgs()[0];
        ResultSet rs = st.getResultSet();
        MappedStatement mappedStatement = (MappedStatement) ReflectUtil.getFieldValue(invocation.getTarget(), "mappedStatement");
        return queryList(invocation, rs, mappedStatement);
    }

    private Object queryList(Invocation invocation, ResultSet rs, MappedStatement mappedStatement) throws InvocationTargetException, IllegalAccessException, SQLException {
        Object result;
        Class mappedClass = mappedStatement.getResultMaps().get(0).getType();
        if (mappedClass.getPackage().getName().equals("java.lang")
                || mappedClass.getPackage().getName().equals("java.util")
                || mappedClass.getPackage().getName().equals("java.math")) {
            result = invocation.proceed();
        } else {
            result = mapRow(rs, mappedClass);
        }
        return result;
    }

    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    public void setProperties(Properties properties) {
    }

    public List<Object> mapRow(ResultSet rs, Class mappedClass) throws SQLException {
        Assert.state(mappedClass != null, "Mapped class was not specified");
        List<Object> list = new ArrayList<>();
        ResultSetMetaData rsmd = rs.getMetaData();
        Map<String, PropertyDescriptor> mappedFields = getMappedFields(mappedClass);
        int columnCount = rsmd.getColumnCount();
        try {
            while (rs.next()) {
                Object mappedObject = mappedClass.newInstance();
                BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(mappedObject);
                for (int index = 1; index <= columnCount; index++) {
                    String column = lookupColumnName(rsmd, index);
                    PropertyDescriptor pd = mappedFields.get(lowerCaseName(column.replaceAll(" ", "")));
                    if (pd != null) {
                        Ignore ignore = pd.getWriteMethod().getAnnotation(Ignore.class);
                        if (ignore != null) {
                            continue;
                        }
                        try {
                            Object value = getColumnValue(rs, index, pd);
                            if (EnumValue.class.isAssignableFrom(pd.getPropertyType())) {
                                Object[] enumConstants = pd.getPropertyType().getEnumConstants();
                                EnumValue ev = null;
                                for (Object v : enumConstants) {
                                    EnumValue v1 = (EnumValue) v;
                                    if (v1.getValue().equals(value)) {
                                        ev = v1;
                                    }
                                }
                                if (value != null && ev == null) {
                                    throw new RuntimeException(pd.getPropertyType().getName() + " enum value 不存在:" + value);
                                }
                                value = ev;
                            }
                            bw.setPropertyValue(pd.getName(), value);
                        } catch (NotWritablePropertyException ex) {
                            throw new RuntimeException(
                                    "Unable to map column " + column + " to property " + pd.getName(), ex);
                        }
                    }
                }
                list.add(mappedObject);
            }
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    protected String lowerCaseName(String name) {
        return name.toLowerCase(Locale.US);
    }

    protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {
        return getResultSetValue(rs, index, pd.getPropertyType());
    }

    @UsesJava7  // guard optional use of JDBC 4.1 (safe with 1.6 due to getObjectWithTypeAvailable check)
    public static Object getResultSetValue(ResultSet rs, int index, Class<?> requiredType) throws SQLException {
        if (requiredType == null) {
            return getResultSetValue(rs, index);
        }

        Object value;

        // Explicitly extract typed value, as far as possible.
        if (String.class == requiredType) {
            return rs.getString(index);
        } else if (boolean.class == requiredType || Boolean.class == requiredType) {
            value = rs.getBoolean(index);
        } else if (byte.class == requiredType || Byte.class == requiredType) {
            value = rs.getByte(index);
        } else if (short.class == requiredType || Short.class == requiredType) {
            value = rs.getShort(index);
        } else if (int.class == requiredType || Integer.class == requiredType) {
            value = rs.getInt(index);
        } else if (long.class == requiredType || Long.class == requiredType) {
            value = rs.getLong(index);
        } else if (float.class == requiredType || Float.class == requiredType) {
            value = rs.getFloat(index);
        } else if (double.class == requiredType || Double.class == requiredType ||
                Number.class == requiredType) {
            value = rs.getDouble(index);
        } else if (BigDecimal.class == requiredType) {
            return rs.getBigDecimal(index);
        } else if (java.sql.Date.class == requiredType) {
            return rs.getDate(index);
        } else if (java.sql.Time.class == requiredType) {
            return rs.getTime(index);
        } else if (java.sql.Timestamp.class == requiredType || java.util.Date.class == requiredType) {
            return rs.getTimestamp(index);
        } else if (byte[].class == requiredType) {
            return rs.getBytes(index);
        } else if (Blob.class == requiredType) {
            return rs.getBlob(index);
        } else if (Clob.class == requiredType) {
            return rs.getClob(index);
        } else {
            return getResultSetValue(rs, index);
        }

        // Perform was-null check if necessary (for results that the JDBC driver returns as primitives).
        return (rs.wasNull() ? null : value);
    }

    public static Object getResultSetValue(ResultSet rs, int index) throws SQLException {
        Object obj = rs.getObject(index);
        String className = null;
        if (obj != null) {
            className = obj.getClass().getName();
        }
        if (obj instanceof Blob) {
            Blob blob = (Blob) obj;
            obj = blob.getBytes(1, (int) blob.length());
        } else if (obj instanceof Clob) {
            Clob clob = (Clob) obj;
            obj = clob.getSubString(1, (int) clob.length());
        } else if ("oracle.sql.TIMESTAMP".equals(className) || "oracle.sql.TIMESTAMPTZ".equals(className)) {
            obj = rs.getTimestamp(index);
        } else if (className != null && className.startsWith("oracle.sql.DATE")) {
            String metaDataClassName = rs.getMetaData().getColumnClassName(index);
            if ("java.sql.Timestamp".equals(metaDataClassName) || "oracle.sql.TIMESTAMP".equals(metaDataClassName)) {
                obj = rs.getTimestamp(index);
            } else {
                obj = rs.getDate(index);
            }
        } else if (obj != null && obj instanceof java.sql.Date) {
            if ("java.sql.Timestamp".equals(rs.getMetaData().getColumnClassName(index))) {
                obj = rs.getTimestamp(index);
            }
        }
        return obj;
    }

    private Map<String, PropertyDescriptor> getMappedFields(Class mappedClass) {
        Map<String, PropertyDescriptor> map = ResultTypeInterceptor.cacheFields.get(mappedClass);
        if (map == null) {
            synchronized (mappedClass) {
                map = ResultTypeInterceptor.cacheFields.get(mappedClass);
                if (map == null) {
                    initialize(mappedClass);
                    map = ResultTypeInterceptor.cacheFields.get(mappedClass);
                }
            }
        }
        return map;
    }

    protected void initialize(Class mappedClass) {
        Map<String, PropertyDescriptor> mappedFields = new HashMap<>();
        Set<String> mappedProperties = new HashSet<>();
        PropertyDescriptor[] pds = BeanUtils.getPropertyDescriptors(mappedClass);
        for (PropertyDescriptor pd : pds) {
            if (pd.getWriteMethod() != null) {
                mappedFields.put(lowerCaseName(pd.getName()), pd);
                String underscoredName = underscoreName(pd.getName());
                if (!lowerCaseName(pd.getName()).equals(underscoredName)) {
                    mappedFields.put(underscoredName, pd);
                }
                mappedProperties.add(pd.getName());
            }
        }
        cacheFields.put(mappedClass, mappedFields);
    }

    public static String lookupColumnName(ResultSetMetaData resultSetMetaData, int columnIndex) throws SQLException {
        String name = resultSetMetaData.getColumnLabel(columnIndex);
        if (name == null || name.length() < 1) {
            name = resultSetMetaData.getColumnName(columnIndex);
        }
        return name;
    }

    protected String underscoreName(String name) {
        if (!StringUtils.hasLength(name)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        result.append(lowerCaseName(name.substring(0, 1)));
        for (int i = 1; i < name.length(); i++) {
            String s = name.substring(i, i + 1);
            String slc = lowerCaseName(s);
            if (!s.equals(slc)) {
                result.append("_").append(slc);
            } else {
                result.append(s);
            }
        }
        return result.toString();
    }

    private static class ReflectUtil {
        /**
         * 利用反射获取指定对象的指定属性
         *
         * @param obj       目标对象
         * @param fieldName 目标属性
         * @return 目标属性的值
         */
        public static Object getFieldValue(Object obj, String fieldName) {
            Object result = null;
            Field field = ReflectUtil.getField(obj, fieldName);
            if (field != null) {
                field.setAccessible(true);
                try {
                    result = field.get(obj);
                } catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return result;
        }

        /**
         * 利用反射获取指定对象里面的指定属性
         *
         * @param obj       目标对象
         * @param fieldName 目标属性
         * @return 目标字段
         */
        private static Field getField(Object obj, String fieldName) {
            Field field = null;
            for (Class<?> clazz = obj.getClass(); clazz != Object.class; clazz = clazz.getSuperclass()) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    //这里不用做处理，子类没有该字段可能对应的父类有，都没有就返回null。
                }
            }
            return field;
        }

        /**
         * 利用反射设置指定对象的指定属性为指定的值
         *
         * @param obj        目标对象
         * @param fieldName  目标属性
         * @param fieldValue 目标值
         */
        public static void setFieldValue(Object obj, String fieldName,
                                         Object fieldValue) {
            Field field = ReflectUtil.getField(obj, fieldName);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    field.set(obj, fieldValue);
                } catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

}