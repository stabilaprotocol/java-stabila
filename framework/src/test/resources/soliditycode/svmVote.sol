contract TestVote {

  /**
   * @dev Contract can accept value while creating.
   */
  constructor() public payable {}

  /**
   * @dev Cd `amount` balance of contract to get resource for `receiver`
   * which type is `res` (0 for bandwidth, 1 for ucr).
   */
  function cd(address payable receiver, uint amount, uint res) external {
    receiver.cd(amount, res);
  }

  /**
   * @dev Uncd specific balance to get corresponding balance.You can use
   * `receiver' and 'res'  (0 for bandwidth, 1 for ucr) parameters to
   * uncd specific balance.
   */
  function uncd(address payable receiver, uint res) external {
    receiver.uncd(res);
  }

  /**
       * @dev Vote executive in `srList` array and every executive will get correspond
       * stabila power in `tpList` array.
       */
      function voteExecutive(address[] calldata srList, uint[] calldata tpList) external {
          vote(srList, tpList);
      }

  /**
   * @dev Withdraw all allowance and reward to contract balance. Return actually withdraw amount.
   */
  function withdrawReward() external returns(uint) {
    return withdrawreward();
  }

  /**
   * @dev query all allowance and reward of contract account. Return contract's all allowance.
   */
  function queryRewardBalance() external view returns(uint) {
    return rewardBalance();
  }

  /**
   * @dev Judge whether the address is a candidate address.If the address is a candidate address,
   * return `true`, or return `false`.
   */
  function isExecutive(address sr) external view returns(bool) {
    return isSrCandidate(sr);
  }

  /**
   * @dev Query vote count of `from` votes for `to`. Return corresponding vote count.
   */
  function queryVoteCount(address from, address to) external view returns(uint) {
    return voteCount(from, to);
  }

  /**
   * @dev Query total vote count of `owner`. Return owner's total vote count.
   */
  function queryTotalVoteCount(address owner) external view returns(uint) {
    return totalVoteCount(owner);
  }

  /**
  * @dev Query `owner` recevied vote count. Return owner's received vote count.
  */
  function queryReceivedVoteCount(address owner) external view returns(uint) {
    return receivedVoteCount(owner);
  }

  /**
   * @dev Query `owner` used vote count. Return owner's used vote count.
   */
  function queryUsedVoteCount(address owner) external view returns(uint) {
    return usedVoteCount(owner);
  }

  /**
   * @dev Execute self destruct and transfer all balance and asset of contract to target address.
   */
  function killme(address payable target) external {
    selfdestruct(target);
  }
}