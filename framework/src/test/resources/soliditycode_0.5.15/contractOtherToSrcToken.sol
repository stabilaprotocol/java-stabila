//pragma solidity ^0.4.24;

contract ConvertType {

constructor() payable public{}

function() payable external{}

//function stringToSrctoken(address payable toAddress, string memory tokenStr, uint256 tokenValue) public {
// srcToken t = srcToken(tokenStr); // ERROR
// toAddress.transferToken(tokenValue, tokenStr); // ERROR
//}

function uint256ToSrctoken(address payable toAddress, uint256 tokenValue, uint256 tokenInt)  public {
  srcToken t = srcToken(tokenInt); // OK
  toAddress.transferToken(tokenValue, t); // OK
  toAddress.transferToken(tokenValue, tokenInt); // OK
}

function addressToSrctoken(address payable toAddress, uint256 tokenValue, address adr) public {
  srcToken t = srcToken(adr); // OK
  toAddress.transferToken(tokenValue, t); // OK
//toAddress.transferToken(tokenValue, adr); // ERROR
}

//function bytesToSrctoken(address payable toAddress, bytes memory b, uint256 tokenValue) public {
 // srcToken t = srcToken(b); // ERROR
 // toAddress.transferToken(tokenValue, b); // ERROR
//}

function bytes32ToSrctoken(address payable toAddress, uint256 tokenValue, bytes32 b32) public {
  srcToken t = srcToken(b32); // OK
  toAddress.transferToken(tokenValue, t); // OK
// toAddress.transferToken(tokenValue, b32); // ERROR
}

//function arrayToSrctoken(address payable toAddress, uint256[] memory arr, uint256 tokenValue) public {
//srcToken t = srcToken(arr); // ERROR
// toAddress.transferToken(tokenValue, arr); // ERROR
//}
}