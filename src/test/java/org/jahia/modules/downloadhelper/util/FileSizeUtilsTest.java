package org.jahia.modules.downloadhelper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link FileSizeUtils#format(long)}.
 *
 * <p>{@link java.text.DecimalFormat} uses the JVM-default {@link java.util.Locale} for grouping
 * separators, so assertions on values below 1 KiB avoid hard-coding a specific thousands separator.
 * Boundaries at and above 1 KiB always produce a short mantissa with no grouping separator.</p>
 */
@DisplayName("FileSizeUtils")
class FileSizeUtilsTest {

    @Nested
    @DisplayName("non-positive guard")
    class NonPositiveGuard {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -1024L, Long.MIN_VALUE})
        @DisplayName("returns '0 B' for zero and negative values")
        void zeroAndNegative(long bytes) {
            assertThat(FileSizeUtils.format(bytes)).isEqualTo("0 B");
        }
    }

    @Nested
    @DisplayName("byte range (< 1 KiB)")
    class ByteRange {

        @Test
        @DisplayName("1 byte formats as '1 B'")
        void oneByte() {
            assertThat(FileSizeUtils.format(1L)).isEqualTo("1 B");
        }

        @Test
        @DisplayName("1023 bytes uses the B unit (locale-safe: ends with ' B')")
        void justBelowKib() {
            // DecimalFormat may add a grouping separator depending on the JVM locale,
            // so we assert on the unit and that the leading digits represent 1023.
            final String result = FileSizeUtils.format(1023L);
            assertThat(result).endsWith(" B");
            // Strip non-digit/dot chars and verify the numeric portion represents 1023.
            final String digits = result.replace(" B", "").replaceAll("[^0-9]", "");
            assertThat(digits).isEqualTo("1023");
        }
    }

    @Nested
    @DisplayName("KiB range (1 KiB – < 1 MiB)")
    class KibRange {

        @Test
        @DisplayName("1024 bytes formats as '1 KiB'")
        void oneKib() {
            assertThat(FileSizeUtils.format(1024L)).isEqualTo("1 KiB");
        }

        @Test
        @DisplayName("1536 bytes (1.5 KiB) formats with value 1.5 and unit KiB")
        void oneAndHalfKib() {
            // DecimalFormat uses the JVM-default Locale for the decimal separator:
            // '.' in English/US, ',' in French/German, etc. Assert on unit + leading digits only.
            final String result = FileSizeUtils.format(1536L);
            // Chain unit + leading-digit checks into one assertion; the fractional part must be
            // exactly 5 (either "1.5 KiB" or "1,5 KiB"), so the digit-only form is "15".
            assertThat(result)
                    .endsWith(" KiB")
                    .startsWith("1")
                    .satisfies(s -> assertThat(s.replaceAll("[^0-9]", "")).isEqualTo("15"));
        }

        @Test
        @DisplayName("1 KiB - 1 byte (1023 bytes) stays in B unit, not KiB")
        void justBelowKibUnit() {
            assertThat(FileSizeUtils.format(1023L)).endsWith(" B");
        }

        @Test
        @DisplayName("1 MiB - 1 byte stays in KiB unit")
        void justBelowMib() {
            // 1048575 bytes = 1024 KiB - 1 byte; rounds to 1024 KiB in the formatter
            final String result = FileSizeUtils.format(1048575L);
            assertThat(result).endsWith(" KiB");
        }
    }

    @Nested
    @DisplayName("MiB range (1 MiB – < 1 GiB)")
    class MibRange {

        @Test
        @DisplayName("1 MiB (1048576 bytes) formats as '1 MiB'")
        void oneMib() {
            assertThat(FileSizeUtils.format(1024L * 1024L)).isEqualTo("1 MiB");
        }

        @Test
        @DisplayName("1.5 MiB formats with value 1.5 and unit MiB")
        void oneAndHalfMib() {
            final String result = FileSizeUtils.format(1024L * 1024L + 512L * 1024L);
            assertThat(result).endsWith(" MiB");
            assertThat(result.replaceAll("[^0-9]", "")).isEqualTo("15");
        }
    }

    @Nested
    @DisplayName("GiB range (1 GiB – < 1 TiB)")
    class GibRange {

        @Test
        @DisplayName("1 GiB (1073741824 bytes) formats as '1 GiB'")
        void oneGib() {
            assertThat(FileSizeUtils.format(1024L * 1024L * 1024L)).isEqualTo("1 GiB");
        }

        @Test
        @DisplayName("1.5 GiB formats with value 1.5 and unit GiB")
        void oneAndHalfGib() {
            final String result = FileSizeUtils.format(1024L * 1024L * 1024L + 512L * 1024L * 1024L);
            assertThat(result).endsWith(" GiB");
            assertThat(result.replaceAll("[^0-9]", "")).isEqualTo("15");
        }
    }

    @Nested
    @DisplayName("TiB range and beyond")
    class TibRange {

        @Test
        @DisplayName("1 TiB formats as '1 TiB'")
        void oneTib() {
            assertThat(FileSizeUtils.format(1024L * 1024L * 1024L * 1024L)).isEqualTo("1 TiB");
        }

        @Test
        @DisplayName("Long.MAX_VALUE does not throw — index is capped at TiB")
        void longMaxValueDoesNotThrow() {
            assertThatCode(() -> FileSizeUtils.format(Long.MAX_VALUE)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Long.MAX_VALUE result ends with ' TiB' (index capped)")
        void longMaxValueUsesTibUnit() {
            assertThat(FileSizeUtils.format(Long.MAX_VALUE)).endsWith(" TiB");
        }
    }
}
