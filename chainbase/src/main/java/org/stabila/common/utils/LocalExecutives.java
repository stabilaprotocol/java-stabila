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

package org.stabila.common.utils;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.stabila.common.crypto.ECKey;
import org.stabila.common.crypto.SignInterface;
import org.stabila.common.crypto.SignUtils;
import org.stabila.core.config.Parameter.ChainConstant;

@Slf4j(topic = "app")
public class LocalExecutives {

  @Getter
  private List<String> privateKeys = Lists.newArrayList();

  private byte[] executiveAccountAddress;

  public LocalExecutives() {
  }

  public LocalExecutives(String privateKey) {
    addPrivateKeys(privateKey);
  }

  public LocalExecutives(List<String> privateKeys) {
    setPrivateKeys(privateKeys);
  }

  public byte[] getExecutiveAccountAddress(boolean isECKeyCryptoEngine) {
    if (executiveAccountAddress == null) {
      byte[] privateKey = ByteArray.fromHexString(getPrivateKey());
      final SignInterface cryptoEngine = SignUtils.fromPrivate(privateKey, isECKeyCryptoEngine);
      this.executiveAccountAddress = cryptoEngine.getAddress();
    }
    return executiveAccountAddress;
  }

  public void setExecutiveAccountAddress(final byte[] localExecutiveAccountAddress) {
    this.executiveAccountAddress = localExecutiveAccountAddress;
  }

  public void initExecutiveAccountAddress(boolean isECKeyCryptoEngine) {
    if (executiveAccountAddress == null) {
      byte[] privateKey = ByteArray.fromHexString(getPrivateKey());
      final SignInterface ecKey = SignUtils.fromPrivate(privateKey,
          isECKeyCryptoEngine);
      this.executiveAccountAddress = ecKey.getAddress();
    }
  }

  /**
   * Private key of ECKey.
   */
  public void setPrivateKeys(final List<String> privateKeys) {
    if (CollectionUtils.isEmpty(privateKeys)) {
      return;
    }
    for (String privateKey : privateKeys) {
      validate(privateKey);
    }
    this.privateKeys = privateKeys;
  }

  private void validate(String privateKey) {
    if (StringUtils.startsWithIgnoreCase(privateKey, "0X")) {
      privateKey = privateKey.substring(2);
    }

    if (StringUtils.isNotBlank(privateKey)
        && privateKey.length() != ChainConstant.PRIVATE_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "Private key(" + privateKey + ") must be " + ChainConstant.PRIVATE_KEY_LENGTH
              + "-bits hex string.");
    }
  }

  public void addPrivateKeys(String privateKey) {
    validate(privateKey);
    this.privateKeys.add(privateKey);
  }

  //get the first one recently
  public String getPrivateKey() {
    if (CollectionUtils.isEmpty(privateKeys)) {
      logger.warn("privateKey is null");
      return null;
    }
    return privateKeys.get(0);
  }

  public byte[] getPublicKey() {
    if (CollectionUtils.isEmpty(privateKeys)) {
      logger.warn("privateKey is null");
      return null;
    }
    byte[] privateKey = ByteArray.fromHexString(getPrivateKey());
    final ECKey ecKey = ECKey.fromPrivate(privateKey);
    return ecKey.getAddress();
  }

}
