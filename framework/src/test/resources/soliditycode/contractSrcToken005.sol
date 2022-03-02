

 contract tokenTest{
     constructor() public payable{}
     // positive case
     function TransferTokenTo(address payable toAddress, srcToken id,uint256 amount) public payable{
         //srcToken id = 0x74657374546f6b656e;
         toAddress.transferToken(amount,id);
     }
    function msgTokenValueAndTokenIdTest() public payable returns(srcToken, uint256, uint256){
        srcToken id = msg.tokenid;
        uint256 tokenValue = msg.tokenvalue;
        uint256 callValue = msg.value;
        return (id, tokenValue, callValue);
    }
 }