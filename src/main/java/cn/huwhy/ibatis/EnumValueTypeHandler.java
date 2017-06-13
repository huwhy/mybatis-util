package cn.huwhy.ibatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import cn.huwhy.interfaces.EnumValue;

/**
 * 自定义EnumValue 转换类
 */
public class EnumValueTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {
    private final Class<E>        type;
    private final List<EnumValue> enums;

    /**
     * 设置配置文件设置的转换类以及枚举类内容，供其他方法更便捷高效的实现
     *
     * @param type 配置文件中设置的转换类
     */
    public EnumValueTypeHandler(Class<E> type) {
        if (type == null)
            throw new IllegalArgumentException("Type argument cannot be null");
        this.type = type;
        List<EnumValue> values = new ArrayList<>();
        for (E e : type.getEnumConstants()) {
            values.add((EnumValue) e);
        }
        enums = values;
        if (this.enums.isEmpty())
            throw new IllegalArgumentException(type.getSimpleName()
                    + " does not represent an enum mybatis.");
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 根据数据库存储类型决定获取类型，本例子中数据库中存放INT类型
        Object val = rs.getObject(columnName);

        if (rs.wasNull()) {
            return null;
        } else {
            // 根据数据库中的code值，定位EnumValue子类
            return (E) locateEnumValue(val);
        }
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        // 根据数据库存储类型决定获取类型，本例子中数据库中存放INT类型
        Object val = rs.getObject(columnIndex);
        if (rs.wasNull()) {
            return null;
        } else {
            // 根据数据库中的code值，定位EnumValue子类
            return (E) locateEnumValue(val);
        }
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        // 根据数据库存储类型决定获取类型，本例子中数据库中存放INT类型
        Object val = cs.getObject(columnIndex);
        if (cs.wasNull()) {
            return null;
        } else {
            // 根据数据库中的code值，定位EnumValue子类
            return (E) locateEnumValue(val);
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType)
            throws SQLException {
        // baseTypeHandler已经帮我们做了parameter的null判断
        ps.setObject(i, ((EnumValue) parameter).getValue());

    }

    /**
     * 枚举类型转换，由于构造函数获取了枚举的子类enums，让遍历更加高效快捷
     *
     * @param value 数据库中存储的自定义code属性
     * @return code对应的枚举类
     */
    private EnumValue locateEnumValue(Object value) {
        for (EnumValue e : enums) {
            if (e.getValue().equals(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的枚举类型：" + value + ",请核对" + type.getSimpleName());
    }
}
