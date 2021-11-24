package org.stabila.core.store;

import com.google.protobuf.ByteString;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.stabila.core.db.StabilaStoreWithRevoking;
import org.stabila.common.utils.ByteArray;
import org.stabila.core.capsule.BytesCapsule;

@Slf4j(topic = "DB")
@Component
public class ExecutiveScheduleStore extends StabilaStoreWithRevoking<BytesCapsule> {

  private static final byte[] ACTIVE_EXECUTIVES = "active_executives".getBytes();
  private static final byte[] CURRENT_SHUFFLED_EXECUTIVES = "current_shuffled_executives".getBytes();

  private static final int ADDRESS_BYTE_ARRAY_LENGTH = 21;

  @Autowired
  private ExecutiveScheduleStore(@Value("executive_schedule") String dbName) {
    super(dbName);
  }

  private void saveData(byte[] species, List<ByteString> executivesAddressList) {
    byte[] ba = new byte[executivesAddressList.size() * ADDRESS_BYTE_ARRAY_LENGTH];
    int i = 0;
    for (ByteString address : executivesAddressList) {
      System.arraycopy(address.toByteArray(), 0,
          ba, i * ADDRESS_BYTE_ARRAY_LENGTH, ADDRESS_BYTE_ARRAY_LENGTH);
      i++;
    }

    this.put(species, new BytesCapsule(ba));
  }

  private List<ByteString> getData(byte[] species) {
    List<ByteString> executivesAddressList = new ArrayList<>();
    return Optional.ofNullable(getUnchecked(species))
        .map(BytesCapsule::getData)
        .map(ba -> {
          int len = ba.length / ADDRESS_BYTE_ARRAY_LENGTH;
          for (int i = 0; i < len; ++i) {
            byte[] b = new byte[ADDRESS_BYTE_ARRAY_LENGTH];
            System.arraycopy(ba, i * ADDRESS_BYTE_ARRAY_LENGTH, b, 0, ADDRESS_BYTE_ARRAY_LENGTH);
            executivesAddressList.add(ByteString.copyFrom(b));
          }
          logger.debug("getExecutives:" + ByteArray.toStr(species) + executivesAddressList);
          return executivesAddressList;
        }).orElseThrow(
            () -> new IllegalArgumentException(
                "not found " + ByteArray.toStr(species) + "Executives"));
  }

  public void saveActiveExecutives(List<ByteString> executivesAddressList) {
    saveData(ACTIVE_EXECUTIVES, executivesAddressList);
  }

  public List<ByteString> getActiveExecutives() {
    return getData(ACTIVE_EXECUTIVES);
  }

  public void saveCurrentShuffledExecutives(List<ByteString> executivesAddressList) {
    saveData(CURRENT_SHUFFLED_EXECUTIVES, executivesAddressList);
  }

  public List<ByteString> getCurrentShuffledExecutives() {
    return getData(CURRENT_SHUFFLED_EXECUTIVES);
  }
}
