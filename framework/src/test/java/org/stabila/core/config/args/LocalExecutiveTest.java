/*
 * java-stabila is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-stabila is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.stabila.core.config.args;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stabila.common.utils.LocalExecutives;

public class LocalExecutiveTest {

  private LocalExecutives localExecutive = new LocalExecutives();

  @Before
  public void setLocalExecutive() {
    localExecutive
        .setPrivateKeys(
            Lists.newArrayList(
                "f31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62"));
  }

  @Test
  public void whenSetNullPrivateKey() {
    localExecutive.setPrivateKeys(null);
  }

  @Test
  public void whenSetEmptyPrivateKey() {
    localExecutive.setPrivateKeys(Lists.newArrayList(""));
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenSetBadFormatPrivateKey() {
    localExecutive.setPrivateKeys(Lists.newArrayList("a111"));
  }

  @Test
  public void whenSetPrefixPrivateKey() {
    localExecutive
        .setPrivateKeys(Lists
            .newArrayList("0xf31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62"));
    localExecutive
        .setPrivateKeys(Lists
            .newArrayList("0Xf31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62"));
  }

  @Test
  public void getPrivateKey() {
    Assert.assertEquals(Lists
            .newArrayList("f31db24bfbd1a2ef19beddca0a0fa37632eded9ac666a05d3bd925f01dde1f62"),
        localExecutive.getPrivateKeys());
  }
}
