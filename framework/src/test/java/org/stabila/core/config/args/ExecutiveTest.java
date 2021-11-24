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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.stabila.core.Wallet;
import org.stabila.common.args.Executive;
import org.stabila.common.utils.ByteArray;

public class ExecutiveTest {

  private Executive executive = new Executive();

  /**
   * init executive.
   */
  @Before
  public void setExecutive() {
    executive
        .setAddress(ByteArray.fromHexString(
            Wallet.getAddressPreFixString() + "448d53b2df0cd78158f6f0aecdf60c1c10b15413"));
    executive.setUrl("http://Uranus.org");
    executive.setVoteCount(1000L);
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenSetNullAddressShouldThrowIllegalArgumentException() {
    executive.setAddress(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenSetEmptyAddressShouldThrowIllegalArgumentException() {
    executive.setAddress(new byte[0]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenSetBadFormatAddressShouldThrowIllegalArgumentException() {
    executive
        .setAddress(ByteArray.fromHexString("558d53b2df0cd78158f6f0aecdf60c1c10b15413"));
  }

  @Test
  public void setAddressRight() {
    executive
        .setAddress(ByteArray.fromHexString(
            Wallet.getAddressPreFixString() + "558d53b2df0cd78158f6f0aecdf60c1c10b15413"));
    Assert.assertEquals(
        Wallet.getAddressPreFixString() + "558d53b2df0cd78158f6f0aecdf60c1c10b15413",
        ByteArray.toHexString(executive.getAddress()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenSetNullUrlShouldThrowIllegalArgumentException() {
    executive.setUrl(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void whenSetEmptyUrlShouldThrowIllegalArgumentException() {
    executive.setUrl("");
  }

  @Test
  public void setUrlRight() {
    executive.setUrl("afwe");
  }

  @Test
  public void setVoteCountRight() {
    executive.setVoteCount(Long.MAX_VALUE);
    Assert.assertEquals(Long.MAX_VALUE, executive.getVoteCount());

    executive.setVoteCount(Long.MIN_VALUE);
    Assert.assertEquals(Long.MIN_VALUE, executive.getVoteCount());

    executive.setVoteCount(1000L);
    Assert.assertEquals(1000L, executive.getVoteCount());
  }

  @Test
  public void getVoteCountRight() {
    Assert.assertEquals(1000L, executive.getVoteCount());
  }
}
