//pragma solidity ^0.4.24;

 contract tokenTest{
     constructor() public payable{}
     // positive case
     function TransferTokenTo(address payable toAddress, srcToken id,uint256 amount) public payable{
         //srcToken id = 0x74657374546f6b656e;
         toAddress.transferToken(amount,id);
     }
 }