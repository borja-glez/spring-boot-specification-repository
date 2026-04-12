package com.borjaglez.specrepository.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class GroupedRowTest {

  @Test
  void shouldExposeColumnsAndValues() {
    GroupedRow row = new GroupedRow(List.of("status", "total"), new Object[] {"ACTIVE", 42L});
    assertThat(row.columns()).containsExactly("status", "total");
    assertThat(row.values()).containsExactly("ACTIVE", 42L);
  }

  @Test
  void shouldLookupByIndex() {
    GroupedRow row = new GroupedRow(List.of("a", "b"), new Object[] {1, 2});
    assertThat(row.get(0)).isEqualTo(1);
    assertThat(row.get(1)).isEqualTo(2);
  }

  @Test
  void shouldLookupByColumnName() {
    GroupedRow row = new GroupedRow(List.of("status", "total"), new Object[] {"X", 7L});
    assertThat(row.get("status")).isEqualTo("X");
    assertThat(row.get("total")).isEqualTo(7L);
  }

  @Test
  void shouldRejectUnknownColumn() {
    GroupedRow row = new GroupedRow(List.of("a"), new Object[] {1});
    assertThatIllegalArgumentException()
        .isThrownBy(() -> row.get("missing"))
        .withMessage("unknown column: missing");
  }

  @Test
  void shouldRejectNullColumnLookup() {
    GroupedRow row = new GroupedRow(List.of("a"), new Object[] {1});
    assertThatNullPointerException().isThrownBy(() -> row.get((String) null));
  }

  @Test
  void shouldRejectIndexOutOfBounds() {
    GroupedRow row = new GroupedRow(List.of("a"), new Object[] {1});
    assertThatThrownBy(() -> row.get(2)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> row.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void shouldRejectMismatchedSizes() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new GroupedRow(List.of("a", "b"), new Object[] {1}))
        .withMessageContaining("columns and values must have the same size");
  }

  @Test
  void shouldRejectNullColumns() {
    assertThatNullPointerException()
        .isThrownBy(() -> new GroupedRow(null, new Object[] {1}))
        .withMessage("columns must not be null");
  }

  @Test
  void shouldRejectNullValues() {
    assertThatNullPointerException()
        .isThrownBy(() -> new GroupedRow(List.of("a"), null))
        .withMessage("values must not be null");
  }

  @Test
  void valuesShouldBeDefensiveCopy() {
    Object[] source = new Object[] {1, 2};
    GroupedRow row = new GroupedRow(List.of("a", "b"), source);
    source[0] = 99;
    assertThat(row.get(0)).isEqualTo(1);
    Object[] returned = row.values();
    returned[1] = 99;
    assertThat(row.get(1)).isEqualTo(2);
  }

  @Test
  void equalsAndHashCodeShouldRespectContent() {
    GroupedRow a = new GroupedRow(List.of("c"), new Object[] {1});
    GroupedRow b = new GroupedRow(List.of("c"), new Object[] {1});
    GroupedRow differentValue = new GroupedRow(List.of("c"), new Object[] {2});
    GroupedRow differentColumns = new GroupedRow(List.of("d"), new Object[] {1});
    assertThat(a).isEqualTo(a);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(differentValue);
    assertThat(a).isNotEqualTo(differentColumns);
    assertThat(a).isNotEqualTo("not a row");
    assertThat(a).isNotEqualTo(null);
  }

  @Test
  void toStringShouldIncludeColumnsAndValues() {
    GroupedRow row = new GroupedRow(List.of("status", "total"), new Object[] {"X", 7L});
    assertThat(row.toString()).isEqualTo("GroupedRow{status=X, total=7}");
  }
}
