
contract svmAssetIssue001 {
    constructor() payable public{}

    function tokenIssue(bytes32 name, bytes32 abbr, uint64 totalSupply, uint8 precision) public returns (uint) {
        return assetissue(name, abbr, totalSupply, precision);
    }

    function updateAsset(srcToken tokenId, string memory url, string memory desc) public returns (bool) {
        return updateasset(tokenId, bytes(url), bytes(desc));
    }

    function updateOtherAccountAsset(string memory url, string memory desc) public returns (bool) {
        srcToken tokenId = srcToken(1000004);
        return updateasset(tokenId, bytes(url), bytes(desc));
    }

    function updateAssetOnBytes(srcToken tokenId, bytes memory url, bytes memory desc) public returns (bool) {
        return updateasset(tokenId, url, desc);
    }

    function transferToken(address payable toAddress, uint256 tokenValue, srcToken id) payable public {
        toAddress.transferToken(tokenValue, id);
    }
}