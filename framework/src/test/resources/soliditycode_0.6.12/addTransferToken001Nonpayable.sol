

 contract IllegalDecorate {

    constructor() payable public{}

    fallback() payable external{}

    function transferTokenWithOutPayable(address payable toAddress,srcToken id, uint256 tokenValue)public {

        toAddress.transferToken(tokenValue, id);
    }
}