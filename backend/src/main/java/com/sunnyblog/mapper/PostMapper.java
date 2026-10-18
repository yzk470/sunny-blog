package com.sunnyblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunnyblog.dto.PostTagRow;
import com.sunnyblog.entity.Post;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface PostMapper extends BaseMapper<Post> {

    @Select("select coalesce(sum(view_count), 0) from blog_post")
    long sumViews();

    @Update("update blog_post set view_count = view_count + #{delta} where id = #{id}")
    int incrementViews(@Param("id") Long id, @Param("delta") long delta);

    @Update("update blog_post set like_count = like_count + 1 where id = #{id}")
    int incrementLikes(@Param("id") Long id);

    @Update("""
            update blog_post
            set comment_count = case
                when comment_count + #{delta} < 0 then 0
                else comment_count + #{delta}
            end
            where id = #{id}
            """)
    int addCommentCount(@Param("id") Long id, @Param("delta") int delta);

    @Select("""
            <script>
            select pt.post_id, t.id, t.name, t.slug, t.color
            from blog_post_tag pt
            join blog_tag t on t.id = pt.tag_id
            where pt.post_id in
            <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
            </foreach>
            order by t.id
            </script>
            """)
    List<PostTagRow> selectTagsByPostIds(@Param("postIds") List<Long> postIds);
}
