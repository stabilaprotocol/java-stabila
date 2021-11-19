contract UcrOfTransferFailedTest {
    constructor() payable public {

    }

    function testTransferTokenCompiledLongMax() payable public{
            payable(address(0x1)).transferToken(1,9223372036855775827);
    }

    function testTransferTokenCompiled() payable public{
        payable(address(0x1)).transferToken(1,1);
    }

    function testTransferTokenCompiledLongMin() payable public{
        //address(0x1).transferToken(1,-9223372036855775828);
    }

    function testTransferTokenCompiledLongMin1() payable public returns(uint256){
        return address(0x2).tokenBalance(trcToken(uint256(int256(-9223372036855775828))));
    }

    function testTransferTokenCompiled1() payable public returns(uint256){
        return address(0x1).tokenBalance(trcToken(1));
    }

    function testTransferTokenCompiledLongMax1() payable public returns(uint256){
        return address(0x2).tokenBalance(trcToken(9223372036855775827));
    }

    function testTransferTokenCompiledTokenId(uint256 tokenid) payable public returns(uint256){
         return address(0x1).tokenBalance(trcToken(tokenid));
    }

    function testTransferTokenTest(address addr ,uint256 tokenid) payable public returns(uint256){
          return  addr.tokenBalance(trcToken(tokenid));
    }

    // InsufficientBalance
    function testTransferStbInsufficientBalance(uint256 i) payable public{
        payable(msg.sender).transfer(i);
    }

    function testSendStbInsufficientBalance(uint256 i) payable public{
        payable(msg.sender).send(i);
    }

    function testTransferTokenInsufficientBalance(uint256 i,trcToken tokenId) payable public{
        payable(msg.sender).transferToken(i, tokenId);
    }

    function testCallStbInsufficientBalance(uint256 i,address payable caller) public {
        caller.call{value:i}(abi.encodeWithSignature("test()"));
    }

    function testCreateStbInsufficientBalance(uint256 i) payable public {
        (new Caller){value:i}();
    }

    // NonexistentTarget

    function testSendStbNonexistentTarget(uint256 i,address payable nonexistentTarget) payable public {
        nonexistentTarget.send(i);
    }

    function testSendStbRevert(uint256 i,address payable nonexistentTarget) payable public {
        nonexistentTarget.send(i);
        revert();
    }

    function testTransferStbNonexistentTarget(uint256 i,address payable nonexistentTarget) payable public {
        nonexistentTarget.transfer(i);
    }

    function testTransferStbrevert(uint256 i,address payable nonexistentTarget) payable public{
        nonexistentTarget.transfer(i);
        revert();
    }

    function testTransferTokenNonexistentTarget(uint256 i,address payable nonexistentTarget, trcToken tokenId) payable public {
        nonexistentTarget.transferToken(i, tokenId);
    }

    function testTransferTokenRevert(uint256 i,address payable nonexistentTarget, trcToken tokenId) payable public {
        nonexistentTarget.transferToken(i, tokenId);
        revert();
    }

    function testCallStbNonexistentTarget(uint256 i,address payable nonexistentTarget) payable public {
        nonexistentTarget.call{value:i}(abi.encodeWithSignature("test()"));
    }

    function testSuicideNonexistentTarget(address payable nonexistentTarget) payable public {
         selfdestruct(nonexistentTarget);
    }

    function testSuicideRevert(address payable nonexistentTarget) payable public {
         selfdestruct(nonexistentTarget);
         revert();
    }

    // target is self
    function testTransferStbSelf(uint256 i) payable public{
        address payable self = payable(address(uint160(address(this))));
        self.transfer(i);
    }

    function testSendStbSelf(uint256 i) payable public{
        address payable self = payable(address(uint160(address(this))));
        self.send(i);
    }

    function testTransferTokenSelf(uint256 i,trcToken tokenId) payable public{
        address payable self = payable(address(uint160(address(this))));
        self.transferToken(i, tokenId);
    }

    event Deployed(address addr, uint256 salt, address sender);
            function deploy(bytes memory code, uint256 salt) public returns(address){
                address addr;
                assembly {
                    addr := create2(10, add(code, 0x20), mload(code), salt)
                    //if iszero(extcodesize(addr)) {
                    //    revert(0, 0)
                    //}
                }
                //emit Deployed(addr, salt, msg.sender);
                return addr;
            }
            function deploy2(bytes memory code, uint256 salt) public returns(address){
                    address addr;
                    assembly {
                        addr := create2(300, add(code, 0x20), mload(code), salt)
                        //if iszero(extcodesize(addr)) {
                        //    revert(0, 0)
                        //}
                    }
                    //emit Deployed(addr, salt, msg.sender);
                    return addr;
                }
}



contract Caller {
    constructor() payable public {}
    function test() payable public {}
}