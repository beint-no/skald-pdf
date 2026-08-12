package org.skaldpdf.pdf;

import java.util.List;
import java.util.Map;

sealed interface CosValue {
    record CosNull() implements CosValue {
    }

    record CosBoolean(boolean value) implements CosValue {
    }

    record CosNumber(String lexicalValue) implements CosValue {
    }

    record CosName(String value) implements CosValue {
    }

    record CosString(byte[] bytes) implements CosValue {
        public CosString {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    record CosArray(List<CosValue> values) implements CosValue {
        public CosArray {
            values = List.copyOf(values);
        }
    }

    record CosDictionary(Map<String, CosValue> values) implements CosValue {
        public CosDictionary {
            values = Map.copyOf(values);
        }

        CosValue get(String name) {
            return values.get(name);
        }
    }

    record CosStream(CosDictionary dictionary, byte[] encodedBytes) implements CosValue {
        public CosStream {
            encodedBytes = encodedBytes.clone();
        }

        @Override
        public byte[] encodedBytes() {
            return encodedBytes.clone();
        }
    }

    record CosReference(int objectNumber, int generation) implements CosValue {
        public CosReference {
            if (objectNumber <= 0 || generation < 0) {
                throw new IllegalArgumentException("Invalid PDF object reference");
            }
        }
    }
}
