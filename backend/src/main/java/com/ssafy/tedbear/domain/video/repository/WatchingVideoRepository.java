package com.ssafy.tedbear.domain.video.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ssafy.tedbear.domain.member.entity.Member;
import com.ssafy.tedbear.domain.video.entity.Video;
import com.ssafy.tedbear.domain.video.entity.WatchingVideo;

@Repository
public interface WatchingVideoRepository extends JpaRepository<WatchingVideo, Long> {
	Optional<WatchingVideo> findTop1ByMemberAndVideoStatusOrderByUpdatedDateDesc(Member member, boolean videoStatus);

	Optional<WatchingVideo> findByMemberAndVideo(Member member, Video video);
}
