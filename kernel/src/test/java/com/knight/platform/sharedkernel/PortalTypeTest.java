package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PortalTypeTest {

    @Test
    void fromProfileId_withIndirectPrefix_returnsIndirect() {
        assertThat(PortalType.fromProfileId("ind:12345")).isEqualTo(PortalType.INDIRECT);
    }

    @Test
    void fromProfileId_withIndirectPrefixOnly_returnsIndirect() {
        assertThat(PortalType.fromProfileId("ind:")).isEqualTo(PortalType.INDIRECT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"online:srf:12345", "online:abc:xyz", "direct:12345", "client:something"})
    void fromProfileId_withNonIndirectPrefix_returnsClient(String profileId) {
        assertThat(PortalType.fromProfileId(profileId)).isEqualTo(PortalType.CLIENT);
    }

    @Test
    void fromProfileId_withNull_returnsClient() {
        assertThat(PortalType.fromProfileId(null)).isEqualTo(PortalType.CLIENT);
    }

    @Test
    void fromProfileId_withEmptyString_returnsClient() {
        assertThat(PortalType.fromProfileId("")).isEqualTo(PortalType.CLIENT);
    }

    @Test
    void fromProfileId_withBlankString_returnsClient() {
        assertThat(PortalType.fromProfileId("   ")).isEqualTo(PortalType.CLIENT);
    }

    @Test
    void fromProfileId_withIndNotAtStart_returnsClient() {
        assertThat(PortalType.fromProfileId("xind:12345")).isEqualTo(PortalType.CLIENT);
    }

    @Test
    void fromProfileId_withIndWithoutColon_returnsClient() {
        assertThat(PortalType.fromProfileId("ind12345")).isEqualTo(PortalType.CLIENT);
    }

    @Test
    void enumValues_containsBothTypes() {
        assertThat(PortalType.values()).containsExactly(PortalType.CLIENT, PortalType.INDIRECT);
    }
}
