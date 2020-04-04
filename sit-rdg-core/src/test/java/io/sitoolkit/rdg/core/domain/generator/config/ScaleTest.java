package io.sitoolkit.rdg.core.domain.generator.config;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Assert;
import org.junit.Test;

public class ScaleTest {

  @Test
  public void testParse() {
    assertThat(Scale.parse(null).getValue(), is(1.0));
    assertThat(Scale.parse("").getValue(), is(1.0));
    assertThat(Scale.parse("abc").getValue(), is(1.0));
    assertThat(Scale.parse("1").getValue(), is(1.0));
    assertThat(Scale.parse("/2").getValue(), is(0.5));
    assertThat(Scale.parse("1/2").getValue(), is(0.5));
    assertThat(Scale.parse("2").getValue(), is(2.0));
    assertThat(Scale.parse("4/2").getValue(), is(2.0));
  }

  @Test
  public void testScale() {
    Scale scale = Scale.parse("/3");
    Assert.assertEquals(10, scale.apply(30));
    Assert.assertEquals(0, scale.apply(0));
    Assert.assertEquals(0, scale.apply(1));
    Assert.assertEquals(1, scale.apply(2));
    Assert.assertEquals(1, scale.apply(3));
    Assert.assertEquals(1, scale.apply(4));
    Assert.assertEquals(2, scale.apply(5));
  }
}
