export interface InteractionState {
  postId: number;
  commentCount: number;
  likeCount: number;
  liked: boolean;
  canComment: boolean;
  canLike: boolean;
  canUnlike: boolean;
}
