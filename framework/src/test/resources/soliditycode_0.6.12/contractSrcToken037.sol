

contract transferSrc10 {
    function receive(address payable rec) public payable {
        uint256 aamount=address(this).tokenBalance(msg.tokenid);
        uint256 bamount=rec.tokenBalance(msg.tokenid);
        require(msg.tokenvalue==aamount);
        require(aamount==msg.tokenvalue);
        rec.transferToken(aamount,msg.tokenid);
        require(0==address(this).tokenBalance(msg.tokenid));
        require(bamount+aamount==rec.tokenBalance(msg.tokenid));
        (bool success, bytes memory data) =rec.call(abi.encodeWithSignature("checkSrc10(uint256,srcToken,uint256)",bamount+aamount,msg.tokenid,0));
        require(success);

    }
}

contract receiveSrc10 {
    fallback() external payable {}
    function checkSrc10(uint256 amount,srcToken tid,uint256 meamount) public{
        require(amount==address(this).tokenBalance(tid));
        require(meamount==msg.sender.tokenBalance(tid));
    }
}