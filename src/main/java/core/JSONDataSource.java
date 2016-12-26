package core;

import net.sf.testng.databinding.AbstractDataSource;
import net.sf.testng.databinding.DataSource;
import net.sf.testng.databinding.TestInput;
import net.sf.testng.databinding.TestOutput;
import net.sf.testng.databinding.core.error.ErrorCollector;
import net.sf.testng.databinding.core.error.MissingPropertiesException;
import net.sf.testng.databinding.core.error.MultipleConfigurationErrorsException;
import net.sf.testng.databinding.core.error.MultipleSourceErrorsException;
import net.sf.testng.databinding.util.MethodParameter;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;
import static net.sf.testng.databinding.core.util.Types.*;
// JSON data source.
@DataSource(name = "json")
public class JSONDataSource extends AbstractDataSource {
// The name of this data source.
@SuppressWarnings({"unused"})
    public static final String DATA_SOURCE_KEY = "json";
/**
 * The path to the actual data source file.
 * A URL conformant String for an absolute locator or a relative path
 * starting with a slash (/).
 */
    public static final String URL_KEY = "url";
/**
 * The path to the actual file with common data for data source file parameterization.
 * A URL conformant String for an absolute locator or a relative path
 * starting with a slash (/).
 */
    public static final String COMMON_DATA_KEY = "commonData";
/**
 * JSON element. Contains set of values for test parameters marked with {@link TestInput}.
 * Each element of the {@code dataSet} array either {@code testInput} or
 * {@code testOutput} section or both of them.
 */
    public static final String DS_IN = "testInput";
/**
 * JSON element. Contains set of values for test parameters marked with {@link TestOutput}.
 * Each element of the {@code dataSet} array either {@code testInput} or
 * {@code testOutput} section or both of them.
 */
    public static final String DS_OUT = "testOutput";
    private static final Pattern URL_PATTERN = compile("^.*\\.json$");
    private List<MethodParameter> parameters;
    private String url;
    private JSONArray dataSets;
/**
 * Construct a new JSON data source, setting the
 * {@link MethodParameter test method parameters} to load the data for and
 * the {@link Properties properties} describing where to load the data from.
 * @param parameters the test method parameters for which data is to be loaded to set
 * @param properties the properties describing where to load the data from to set
 * @throws Exception if occurs any errors during instance creation
 */
public JSONDataSource(final List<MethodParameter> parameters,
                          final Properties properties) throws Exception {
        this.setParameters(parameters);
        this.setProperties(properties);
    }
/**
 * {@inheritDoc}
 */
@Override
    public boolean hasNext() {
        return this.dataSets.length() != 0;
    }
/**
     * {@inheritDoc}
     */
@Override
    public Object[] next() {
        if (this.dataSets.length() == 0) {
            throw new NoSuchElementException("No next set of test data exists");
        }
        JSONObject dataSet = (JSONObject) this.dataSets.remove(0);
        if (!dataSet.has(DS_IN) && !dataSet.has(DS_OUT)) {
            this.throwMSEException(this.url,
                    "data set doesn't have testInput nor testOutput");
        }
        dataSet = this.joinInputAndOutput(dataSet);
        return this.getData(dataSet);
    }
/**
 * Extracts test parameters data from provided dataSet JSON object.
 * @param data the dataSet JSON object to set
 * @return the extracted typified test parameters
 */
private Object[] getData(JSONObject data) {
        Object[] testData = new Object[this.parameters.size()];
        for (int i = 0; i < this.parameters.size(); i++) {
            MethodParameter param = this.parameters.get(i);
            String paramName = this.extractParameterName(param);
            if (data.has(paramName)) {
                testData[i] = this.getData(param.getType(), data.get(paramName));
            }
        }
        return testData;
    }
/**
 * Extracts typified data from provided JSON object.
 * @param paramType the data type to set
 * @param data the data to set
 * @return the extracted value
 */
private Object getData(Type paramType, Object data) {
        if (isEnumType(paramType)) {
            return this.getEnum(paramType, data);
        } else if (isPrimitiveType(paramType)) {
            return this.getPrimitive(paramType, data);
        } else if (isBigDecimal(paramType)) {
            return BigDecimal.valueOf(Double.valueOf((String) data));
        } else if (isSingleBeanType(paramType)) {
            return this.getBean(paramType, (JSONObject) data);
        } else if (isListOfPrimitivesType(paramType)) {
            return this.getPrimitiveList(paramType, data);
        } else if (isListOfBeansType(paramType)) {
            return this.getBeanList(paramType, data);
        } else {throwMSEException(paramType.toString(), "Unsupported param type");
        }
        return null; // Can't happen
    }
    private boolean isBigDecimal(Type type) {
        return (type instanceof Class<?>) && (type == BigDecimal.class);
    }
/**
 * Extracts the list of beans from provided data.
 * @param paramType the list type to set
 * @param data the data to set
 * @return the extracted value
 */
private Object getBeanList(Type paramType, Object data) {
        List<Object> beans = new LinkedList<>();
        Type type = ((ParameterizedType) paramType).getActualTypeArguments()[0];
        JSONArray array = (JSONArray) data;
        for (int i = 0; i < array.length(); i++) {
            beans.add(this.getBean(type, (JSONObject) array.get(i)));
        }
        return beans;
    }
/**
 * Extracts the list of primitives from provided data.
 * @param paramType the list type to set
 * @param data the data to set
 * @return the extracted value
 */
private Object getPrimitiveList(Type paramType, Object data) {
        List<Object> primitives = new LinkedList<>();
        Type type = ((ParameterizedType) paramType).getActualTypeArguments()[0];
        JSONArray array = (JSONArray) data;
        for (int i = 0; i < array.length(); i++) {
            primitives.add(this.getPrimitive(type, array.get(i)));
        }
        return primitives;
    }
/**
 * Extracts the bean object from provided data.
 * @param paramType the bean type to set
 * @param data the data to set
 * @return the extracted value
 */
@SuppressWarnings({"unchecked"})
    private Object getBean(Type paramType, JSONObject data) {
        try {
            Class<?> clazz = (Class<?>) paramType;
            BeanInfo info = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] descriptors = info.getPropertyDescriptors();
            Map<String, PropertyDescriptor> fields = this.getFields(descriptors);
            Object bean = clazz.newInstance();

            for (String key : (Set<String>) data.keySet()) {
                if (fields.containsKey(key)) {
                    PropertyDescriptor descriptor = fields.get(key);
                    Type type = descriptor.getPropertyType();
                    Object value = this.getData(type, data.get(key));
                    descriptor.getWriteMethod().invoke(bean, value);
                }
            }
            return bean;
        } catch (IntrospectionException | ReflectiveOperationException e) {
            String msg = "Unable to create bean: " + e.getMessage();
            this.throwMSEException(paramType.toString(), msg);
        }
        return null; // Can't happen
    }
/**
 * Extracts the primitive object from provided data.
 * @param type the primitive type to set
 * @param data the data to set
 * @return the extracted value
 */
private Object getPrimitive(Type type, Object data) {
        if (type.equals(String.class)) {
            return data;
        } else if (type == Integer.class || type == int.class) {
            return ((Integer) data).intValue();
        } else if (type == Long.class || type == long.class) {
            return ((Integer) data).longValue();
        } else if (type == Float.class || type == float.class) {
            return ((Double) data).floatValue();
        } else if (type == Double.class || type == double.class) {
            return ((Double) data).doubleValue();
        } else if (type == Boolean.class || type == boolean.class) {
            return ((Boolean) data).booleanValue();
        }
        throw new RuntimeException(); // Can't happen
    }
/**
 * Extracts the enum object from provided data.
 * @param type the enum type to set
 * @param data the data to set
 * @return the extracted value
 */
private Object getEnum(Type type, Object data) {
        for (Field field : ((Class<?>) type).getFields()) {
            try {
                if (field.getName().equals(data)) 	{
                    return field.get(null);
                							}
            } catch (IllegalAccessException ignored) 	{ // Can't happen
}
        }
        return null;
    }
/**
 * Joins the {@code testInput} and {@code testOutput} parts of provided {@code dataSet}.
 * @param dataSet sets the data.
 * @return the object contains data parts of provided set.
 */
@SuppressWarnings({"unchecked"})
    private JSONObject joinInputAndOutput(JSONObject dataSet) {
        JSONObject data = new JSONObject();
        if (null != dataSet.optJSONObject(DS_IN)) {
            JSONObject inputDS = dataSet.getJSONObject(DS_IN);
            for (String key : (Set<String>) inputDS.keySet()) {
                data.put(key, inputDS.get(key));
            }
        }
        if (null != dataSet.optJSONObject(DS_OUT)) {
            JSONObject outputDS = dataSet.getJSONObject(DS_OUT);
            for (String key : (Set<String>) outputDS.keySet()) {
                data.put(key, outputDS.get(key));
            }
        }
        return data;
    }
/**
 * Extracts the name of test method parameter.
 * @param param the test method parameter to set
 * @return the related name
 */
    private String extractParameterName(MethodParameter param) {
        TestInput inputAnnotation = param.getAnnotation(TestInput.class);
        if (inputAnnotation != null) {
            return inputAnnotation.name();
        }
        TestOutput outputAnnotation = param.getAnnotation(TestOutput.class);
        if (outputAnnotation != null) {
            return outputAnnotation.name();
        }
        throw new RuntimeException(); // Can't happen
    }
/**
 * Filters provided property descriptors and return only properties with ancestors
 * @param descriptors the object properties array to set
 * @return the map of filtered object properties where the key is a
 * property name and value is a selected property
 */
    private Map<String, PropertyDescriptor>
    getFields(PropertyDescriptor[] descriptors) {
        Map<String, PropertyDescriptor> candidates = new HashMap<>();
        for (PropertyDescriptor descriptor : descriptors) {
            if (null != descriptor.getWriteMethod()) {
                candidates.put(descriptor.getName(), descriptor);
            }
        }
        return candidates;
    }
/**
 * Checks given data source properties and fails if there are missing
 * properties or unsupported properties values.
 * @param properties the data source properties to set
 * @throws AssertionError if found any properties misconfiguration
 */
private void setProperties(final Properties properties)
            throws AssertionError, IOException {
        if (!properties.containsKey(URL_KEY)) {
            throw new MissingPropertiesException(URL_KEY);
        }
        this.url = (String) properties.get(URL_KEY);
        String jsonStr = null;
        try {
            InputStream is = this.getClass().getResourceAsStream(url);
            jsonStr = IOUtils.toString(is);
        } catch (IOException e) {
            this.throwMSEException(this.url, "Error during file reading");
        }
        try {
            this.dataSets = new JSONArray(jsonStr);
        } catch (JSONException e) {
            this.throwMSEException(this.url, "Error during JSON parsing");
        }
        if (this.dataSets.length() == 0) {
            this.throwMSEException(this.url, "JSON doesn't contain data");
        }
    }
/**
 * Checks given test parameters, init data source's input and output
 * parameters lists and fails if finds any parameters errors.
 * @param parameters the test method parameters to set
 * @throws MultipleConfigurationErrorsException if found any parameters misconfiguration
 */
    private void setParameters(final List<MethodParameter> parameters)
            throws MultipleConfigurationErrorsException {
        if (parameters.isEmpty()) {
            ErrorCollector ec = new ErrorCollector("method parameters");
            ec.addError("no parameters with @TestInput or @TestOutput given");
            throw new MultipleConfigurationErrorsException(Arrays.asList(ec));
        }
        this.parameters = parameters;
    }
/**
 * Construct and throws {@code MultipleSourceErrorsException}.
 * @param targetName the name of parameter for which the errors are to be collected to set
 * @param message the error message to set
 * @throws MultipleSourceErrorsException in any case
 */
    private void throwMSEException(String targetName, String message)
            throws MultipleSourceErrorsException {
        ErrorCollector error = new ErrorCollector(targetName);
        error.addError(message);
        throw new MultipleSourceErrorsException(Arrays.asList(error));
    }
}
