

contract token{
    constructor() payable public{}
    fallback() payable external{}
     function testInCall(address callBAddress,address callCAddress, address toAddress ,uint256 amount,srcToken id) payable public{
         //callBAddress.call(bytes4(keccak256("transC(address,address,uint256,srcToken)")),callCAddress,toAddress,amount,id);
         callBAddress.call(abi.encodeWithSignature("transC(address,address,uint256,srcToken)",callCAddress,toAddress,amount,id));
     }
    function testIndelegateCall(address callBddress,address callAddressC, address toAddress,uint256 amount, srcToken id) payable public{
         callBddress.delegatecall(abi.encodeWithSignature("transC(address,address,uint256,srcToken)",callAddressC,toAddress,amount,id));
     }
 }



contract B{
    constructor() public payable{}
    fallback() external payable{}
    function  transC(address payable callCAddress,address payable toAddress,uint256 amount, srcToken id) payable public{
         callCAddress.call(abi.encodeWithSignature("trans(address,uint256,srcToken)",toAddress,amount,id));
    }
}
contract C{
    constructor() payable public{}
    fallback() payable external{}
    function  trans(address payable toAddress,uint256 amount, srcToken id) payable public{
            toAddress.transferToken(amount,id);
    }

}
