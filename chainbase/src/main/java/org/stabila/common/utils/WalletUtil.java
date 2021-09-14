package org.stabila.common.utils;

import static org.stabila.common.utils.StringUtil.encode58Check;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.stabila.common.crypto.Hash;
import org.stabila.common.parameter.CommonParameter;
import org.stabila.core.capsule.ContractCapsule;
import org.stabila.core.capsule.TransactionCapsule;
import org.stabila.core.exception.ContractValidateException;
import org.stabila.core.exception.PermissionException;
import org.stabila.protos.Protocol.Permission;
import org.stabila.protos.Protocol.Transaction;
import org.stabila.protos.Protocol.Transaction.Contract;
import org.stabila.protos.contract.SmartContractOuterClass.CreateSmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract.ABI;
import org.stabila.protos.contract.SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType;
import org.stabila.protos.contract.SmartContractOuterClass.TriggerSmartContract;

public class WalletUtil {

  public static boolean checkPermissionOperations(Permission permission, Contract contract)
      throws PermissionException {
    ByteString operations = permission.getOperations();
    if (operations.size() != 32) {
      throw new PermissionException("operations size must 32");
    }
    int contractType = contract.getTypeValue();
    boolean b = (operations.byteAt(contractType / 8) & (1 << (contractType % 8))) != 0;
    return b;
  }

  public static byte[] generateContractAddress(Transaction stb) {

    CreateSmartContract contract = ContractCapsule.getSmartContractFromTransaction(stb);
    byte[] ownerAddress = contract.getOwnerAddress().toByteArray();
    TransactionCapsule stbCap = new TransactionCapsule(stb);
    byte[] txRawDataHash = stbCap.getTransactionId().getBytes();

    byte[] combined = new byte[txRawDataHash.length + ownerAddress.length];
    System.arraycopy(txRawDataHash, 0, combined, 0, txRawDataHash.length);
    System.arraycopy(ownerAddress, 0, combined, txRawDataHash.length, ownerAddress.length);

    return Hash.sha3omit12(combined);

  }


  // for `CREATE2`
  public static byte[] generateContractAddress2(byte[] address, byte[] salt, byte[] code) {
    byte[] mergedData = ByteUtil.merge(address, salt, Hash.sha3(code));
    return Hash.sha3omit12(mergedData);
  }

  public static boolean isConstant(ABI abi, TriggerSmartContract triggerSmartContract)
      throws ContractValidateException {
    try {
      boolean constant = isConstant(abi, getSelector(triggerSmartContract.getData().toByteArray()));
      if (constant && !CommonParameter.getInstance().isSupportConstant()) {
        throw new ContractValidateException("this node don't support constant");
      }
      return constant;
    } catch (ContractValidateException e) {
      throw e;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isConstant(SmartContract.ABI abi, byte[] selector) {

    if (selector == null || selector.length != 4
        || abi.getEntrysList().size() == 0) {
      return false;
    }

    for (int i = 0; i < abi.getEntrysCount(); i++) {
      ABI.Entry entry = abi.getEntrys(i);
      if (entry.getType() != ABI.Entry.EntryType.Function) {
        continue;
      }

      int inputCount = entry.getInputsCount();
      StringBuffer sb = new StringBuffer();
      sb.append(entry.getName());
      sb.append("(");
      for (int k = 0; k < inputCount; k++) {
        ABI.Entry.Param param = entry.getInputs(k);
        sb.append(param.getType());
        if (k + 1 < inputCount) {
          sb.append(",");
        }
      }
      sb.append(")");

      byte[] funcSelector = new byte[4];
      System.arraycopy(Hash.sha3(sb.toString().getBytes()), 0, funcSelector, 0, 4);
      if (Arrays.equals(funcSelector, selector)) {
        if (entry.getConstant() || entry.getStateMutability()
            .equals(StateMutabilityType.View) || entry.getStateMutability()
            .equals(StateMutabilityType.Pure)) {
          return true;
        } else {
          return false;
        }
      }
    }

    return false;
  }

  public static List<String> getAddressStringList(Collection<ByteString> collection) {
    return collection.stream()
        .map(bytes -> encode58Check(bytes.toByteArray()))
        .collect(Collectors.toList());
  }

  public static byte[] getSelector(byte[] data) {
    if (data == null ||
        data.length < 4) {
      return null;
    }

    byte[] ret = new byte[4];
    System.arraycopy(data, 0, ret, 0, 4);
    return ret;
  }

}
