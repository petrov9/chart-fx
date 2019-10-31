package de.gsi.dataset.serializer.spi.iobuffer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.dataset.AxisDescription;
import de.gsi.dataset.serializer.FieldSerialiser;
import de.gsi.dataset.serializer.IoBuffer;
import de.gsi.dataset.serializer.spi.AbstractSerialiser;
import de.gsi.dataset.serializer.spi.BinarySerialiser;
import de.gsi.dataset.serializer.spi.BinarySerialiser.HeaderInfo;
import de.gsi.dataset.serializer.spi.ClassDescriptions;
import de.gsi.dataset.serializer.spi.ClassFieldDescription;
import de.gsi.dataset.serializer.spi.FieldHeader;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

public class IoBufferSerialiser extends AbstractSerialiser {
    private static final Logger LOGGER = LoggerFactory.getLogger(IoBufferSerialiser.class);
    private final IoBuffer ioBuffer;

    public IoBufferSerialiser(final IoBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        ioBuffer = buffer;
        startMarkerFunction = subClassName -> BinarySerialiser.putStartMarker(ioBuffer, subClassName);
        endMarkerFunction = subClassName -> BinarySerialiser.putEndMarker(ioBuffer, subClassName);

        // register primitive and boxed data type handlers
        FieldPrimitiveValueHelper.register(this, ioBuffer);
        FieldPrimitveValueArrayHelper.register(this, ioBuffer);
        FieldBoxedValueHelper.register(this, ioBuffer);
        FieldBoxedValueArrayHelper.register(this, ioBuffer);

        addClassDefinition(new FieldList(ioBuffer, List.class));
        addClassDefinition(new FieldMap(ioBuffer, Map.class));
        addClassDefinition(new FieldDoubleArrayList(ioBuffer, DoubleArrayList.class));
        addClassDefinition(new FieldListAxisDescription(ioBuffer, List.class, AxisDescription.class));

        // addClassDefinition(null, StringHashMapList.class);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.atDebug().addArgument(AbstractSerialiser.class.getName()).addArgument(knownClasses().size())
                    .log("{} added {} known classes");
        }
    }

    protected void deserialise(final Object obj, final FieldHeader fieldRoot,
            final ClassFieldDescription classFieldDescription, final int recursionDepth) throws IllegalAccessException {
        final String ioName = fieldRoot.getFieldName();

        ClassFieldDescription firstMatchingField = null;

        // TODO: check expression below (should be correct -> allows for multiple
        // variables in hierarchy with same name)
//         firstMatchingField = classFieldDescription.getChildren().stream()
//         .filter(e -> e.getFieldName().equals(ioName)).findFirst().orElse(null);
        for (final ClassFieldDescription field : classFieldDescription) {
            if (field.getFieldName().equals(ioName)) {
                firstMatchingField = field;
                break;
            }
        }

        if (firstMatchingField == null) {
            // did not find matching (sub-)field in class
            if (LOGGER.isDebugEnabled()) {
                LOGGER.atDebug().addArgument(ioName).log("did not find matching field: '{}'");
            }

            // check for potential inner fields
            for (FieldHeader fieldHeader : fieldRoot.getChildren()) {
                final String fieldName = fieldHeader.getFieldName();
                Optional<ClassFieldDescription> subFieldDescription = classFieldDescription.getChildren().stream()
                        .filter(e -> e.getFieldName().equals(fieldName)).findFirst();
                if (subFieldDescription.isPresent()) {
                    deserialise(obj, fieldHeader, subFieldDescription.get(), recursionDepth + 1);
                }
            }
            return;
        }

        final Class<?> fieldClass = firstMatchingField.getType();
        if (firstMatchingField.isFinal() && !fieldClass.isInterface()) {
            // cannot set final variables
            LOGGER.atWarn().addArgument(firstMatchingField.getFieldNameRelative()).log("cannot set final field '{}'");
            return;
        }

        // LOGGER.atInfo().addArgument(firstMatchingField.getFieldName()).log("found
        // matching field: '{}'");

        // final List<FieldSerialiser> serialiserList =
        // this.knownClasses().get(fieldClass);
        Optional<FieldSerialiser> serialiser = findFieldSerialiserForKnownClassOrInterface(fieldClass,
                firstMatchingField.getActualTypeArguments());

        if (serialiser.isEmpty()) {
            // no specific serialiser present
            // check for potential inner fields
            for (FieldHeader fieldHeader : fieldRoot.getChildren()) {
                final String fieldName = fieldHeader.getFieldName();
                Optional<ClassFieldDescription> subFieldDescription = classFieldDescription.getChildren().stream()
                        .filter(e -> e.getFieldName().equals(fieldName)).findFirst();
                if (subFieldDescription.isPresent()) {
                    deserialise(obj, fieldHeader, subFieldDescription.get(), recursionDepth + 1);
                }
            }
            return;
        }

        ioBuffer.position(fieldRoot.getDataBufferPosition());
        if (serialiser.isPresent()) {
            serialiser.get().readFrom(firstMatchingField.getMemberClassObject(obj), firstMatchingField);
            return;
        }

        // should not happen/reach here (because of 'serialiser.isEmpty()' above)
        throw new IllegalStateException("should not happen -- cannot serialise field - "
                + firstMatchingField.getFieldNameRelative() + " - class type = " + firstMatchingField.getTypeName());
    }

    @Override
    public Object deserialiseObject(final Object obj) throws IllegalAccessException {
        if (obj == null) {
            throw new IllegalArgumentException("obj must not be null (yet)");
        }

        final HeaderInfo bufferHeader = BinarySerialiser.checkHeaderInfo(ioBuffer);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.atDebug().addArgument(bufferHeader).log("read header = {}");
        }

        // match field header with class field description
        final ClassFieldDescription classFieldDescription = ClassDescriptions.get(obj.getClass());

        FieldHeader fieldRoot = BinarySerialiser.parseIoStream(ioBuffer);
        // deserialise into object
        deserialise(obj, fieldRoot, classFieldDescription, 0);

        return obj;
    }

    public IoBuffer getBuffer() {
        return ioBuffer;
    }

    @Override
    public void serialiseObject(final Object obj) throws IllegalAccessException {
        BinarySerialiser.putHeaderInfo(ioBuffer);

        super.serialiseObject(obj);

        BinarySerialiser.putEndMarker(ioBuffer, "OBJ_ROOT_END");
    }
}
