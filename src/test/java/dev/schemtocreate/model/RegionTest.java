package dev.schemtocreate.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionTest {

    @Test
    @DisplayName("indices follow YZX order, X fastest")
    void usesYzxOrder() {
        Region region = Region.of(4, 3, 2);

        assertThat(region.indexOf(0, 0, 0)).isZero();
        assertThat(region.indexOf(1, 0, 0)).isEqualTo(1);
        assertThat(region.indexOf(0, 0, 1)).isEqualTo(4);
        assertThat(region.indexOf(0, 1, 0)).isEqualTo(8);
    }

    @Test
    @DisplayName("positionOf inverts indexOf across the whole region")
    void indexAndPositionAreInverses() {
        Region region = Region.of(7, 5, 3);

        for (long index = 0; index < region.volume(); index++) {
            BlockPos pos = region.positionOf(index);
            assertThat(region.indexOf(pos.x(), pos.y(), pos.z())).isEqualTo(index);
        }
    }

    @Test
    @DisplayName("volume is computed in long arithmetic")
    void volumeDoesNotOverflow() {
        Region huge = Region.of(2000, 256, 2000);

        assertThat(huge.volume()).isEqualTo(1_024_000_000L);
    }

    @Test
    @DisplayName("a zero dimension yields an empty region")
    void emptyRegion() {
        assertThat(Region.of(0, 10, 10).isEmpty()).isTrue();
        assertThat(Region.of(10, 10, 10).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("containment is checked per axis")
    void containsRespectsBounds() {
        Region region = Region.of(4, 3, 2);

        assertThat(region.contains(new BlockPos(3, 2, 1))).isTrue();
        assertThat(region.contains(new BlockPos(4, 2, 1))).isFalse();
        assertThat(region.contains(new BlockPos(-1, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("a negative entity coordinate floors to the block below")
    void vec3FloorsTowardsNegativeInfinity() {
        assertThat(new Vec3d(-0.5, 3.9, -2.1).containingBlock())
                .isEqualTo(new BlockPos(-1, 3, -3));
    }
}
