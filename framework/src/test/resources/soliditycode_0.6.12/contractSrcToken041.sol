

 contract tokenTest{
     constructor() public payable{}
     // positive case
     function TransferTokenTo(address payable toAddress, srcToken id,uint256 amount) public payable{
         //srcToken id = 0x74657374546f6b656e;
         toAddress.transferToken(amount,id);
     }
 }

contract B{
    uint256 public flag = 0;
    constructor() public payable {}
    fallback() external payable {}

    function setFlag() public payable{
        flag = 1;
    }
}