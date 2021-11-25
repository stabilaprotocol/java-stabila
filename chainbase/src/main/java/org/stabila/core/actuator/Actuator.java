package org.stabila.core.actuator;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.stabila.core.exception.ContractExeException;
import org.stabila.core.exception.ContractValidateException;

public interface Actuator {

  boolean execute(Object result) throws ContractExeException;

  boolean validate() throws ContractValidateException;

  ByteString getOwnerAddress() throws InvalidProtocolBufferException;

  long calcFee();

}
