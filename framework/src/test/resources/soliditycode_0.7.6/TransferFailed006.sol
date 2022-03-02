contract UcrOfTransferFailedTest {
    constructor() payable public {

    }
    // InsufficientBalance
    function testTransferStbInsufficientBalance(uint256 i) payable public{
        msg.sender.transfer(i);
    }

    function testSendStbInsufficientBalance(uint256 i) payable public{
        msg.sender.send(i);
    }

    function testTransferTokenInsufficientBalance(uint256 i,srcToken tokenId) payable public{
        msg.sender.transferToken(i, tokenId);
    }

    function testCallStbInsufficientBalance(uint256 i,address payable caller) public returns (bool,bytes memory){
        return caller.call{value:i}(abi.encodeWithSignature("test()"));
    }

    function testCreateStbInsufficientBalance(uint256 i) payable public {
        (new Caller){value:i}();
    }

    // NonexistentTarget

    function testSendStbNonexistentTarget(uint256 i,address payable nonexistentTarget) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.send(i);
    }

    function testTransferStbNonexistentTarget(uint256 i,address payable nonexistentTarget) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.transfer(i);
    }

    function testTransferTokenNonexistentTarget(uint256 i,address payable nonexistentTarget, srcToken tokenId) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.transferToken(i, tokenId);
    }

    function testCallStbNonexistentTarget(uint256 i,address payable nonexistentTarget) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.call{value:i}(abi.encodeWithSignature("test()"));
    }

    function testSuicideNonexistentTarget(address payable nonexistentTarget) payable public {
         selfdestruct(nonexistentTarget);
    }

    // target is self
    function testTransferStbSelf(uint256 i) payable public{
        require(address(this).balance >= i);
        address payable self = address(uint160(address(this)));
        self.transfer(i);
    }

    function testSendStbSelf(uint256 i) payable public{
        require(address(this).balance >= i);
        address payable self = address(uint160(address(this)));
        self.send(i);
    }

    function testTransferTokenSelf(uint256 i,srcToken tokenId) payable public{
        require(address(this).balance >= i);
        address payable self = address(uint160(address(this)));
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
}



contract Caller {
    constructor() payable public {}
    function test() payable public returns (uint256 ){return 1;}
}