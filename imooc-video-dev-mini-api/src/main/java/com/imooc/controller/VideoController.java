package com.imooc.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.imooc.enums.VideoStatusEnum;
import com.imooc.pojo.Bgm;
import com.imooc.pojo.Comments;
import com.imooc.pojo.Videos;
import com.imooc.service.BgmService;
import com.imooc.service.VideoService;
import com.imooc.utils.FetchVideoCover;
import com.imooc.utils.IMoocJSONResult;
import com.imooc.utils.MergeVideoMp3;
import com.imooc.utils.PagedResult;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@RestController
@Api(value = "视频上传业务接口", tags = "视频上传接口controller")
@RequestMapping("/video")
public class VideoController extends BasicController {
	@Autowired
	private BgmService bgmService;

	@Autowired
	private VideoService videoService;
	@ApiOperation(value = "上传视频", notes = "上传视频的接口")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "form"),
			@ApiImplicitParam(name = "bgmId", value = "背景音乐id", required = false, dataType = "String", paramType = "form"),
			@ApiImplicitParam(name = "videoSeconds", value = "视频的长度", required = true, dataType = "double", paramType = "form"),
			@ApiImplicitParam(name = "videowidth", value = "视频的宽度", required = true, dataType = "int", paramType = "form"),
			@ApiImplicitParam(name = "videoHight", value = "视频的高度", required = true, dataType = "int", paramType = "form"),
			@ApiImplicitParam(name = "desc", value = "视频的描述", required = false, dataType = "String", paramType = "form"),

	})

	@PostMapping(value = "/upload", headers = "content-type=multipart/form-data")
	public IMoocJSONResult upload(String userId, String bgmId, double videoSeconds, int videoWidth, int videoHeight,
			String desc, @ApiParam(value = "短视频", required = true) MultipartFile file) throws Exception {
		if (StringUtils.isBlank(userId)) {
			return IMoocJSONResult.errorMap("用户id不能为空");
		}

		// 保存到数据库中的相对路径
		String uploadPathDB = "/" + userId + "/video";
		String coverPathDB = "/" + userId + "/video";
		FileOutputStream fileOutputStream = null;
		InputStream inputStream = null;
		String finalVideoPath = "";
		try {
			if (file != null) {
				String filename = file.getOriginalFilename();
				
				// abc.mp4
				String arrayFilenameItem[] =  filename.split("\\.");
				String fileNamePrefix = "";
				for (int i = 0 ; i < arrayFilenameItem.length-1 ; i ++) {
					fileNamePrefix += arrayFilenameItem[i];
				}
				// fix bug: 解决小程序端OK，PC端不OK的bug，原因：PC端和小程序端对临时视频的命名不同
                //String fileNamePrefix = fileName.split("\\.")[0];

				
				if (StringUtils.isNoneBlank(filename)) {
					// 文件上传的最终路径
					finalVideoPath = FILE_SPACE + uploadPathDB + "/" + filename;
					// 设置数据库的保存路径
					uploadPathDB = (FILE_SPACE_CHILD + uploadPathDB + "/" + filename);
					coverPathDB = (FILE_SPACE_CHILD + coverPathDB + "/" + fileNamePrefix+".jpg");
					
					File outFile = new File(finalVideoPath);
					if (outFile.getParentFile() != null || !outFile.getParentFile().isDirectory()) {
						// 创建父文件夹
						outFile.getParentFile().mkdirs();
					}

					fileOutputStream = new FileOutputStream(outFile);
					inputStream = file.getInputStream();
					IOUtils.copy(inputStream, fileOutputStream);
				} else {
					return IMoocJSONResult.errorMap("上传出错...");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return IMoocJSONResult.errorMap("上传出错...");
		} finally {
			if (fileOutputStream != null) {
				fileOutputStream.flush();
				fileOutputStream.close();
			}
		}

		// 判断bgmId是否为空,如果不为空
		// 那就查询bgm的信息，并且合并视频，生成新的视频
		if (StringUtils.isNotBlank(bgmId)) {
			Bgm bgm = bgmService.qureyBgmById(bgmId);
			String mp3InuputPath = FILE_SPACE_PARENT + bgm.getPath();
			MergeVideoMp3 tool = new MergeVideoMp3(FFMPEG_EXE);
			String videoInputPath = finalVideoPath;
			String videoOutPutName = UUID.randomUUID().toString() + ".mp4";
			uploadPathDB = FILE_SPACE_CHILD+"/" + userId + "/video" + "/" + videoOutPutName;
			finalVideoPath = FILE_SPACE_PARENT + uploadPathDB;
			tool.convertor(videoInputPath, mp3InuputPath, videoSeconds, finalVideoPath);
		}
         
		System.out.println("uploadPathDB = " + uploadPathDB);
		System.out.println("finalCoverPath = " + finalVideoPath);
		//对视频进行截图
		FetchVideoCover videoInfo = new FetchVideoCover(FFMPEG_EXE);
	    videoInfo.getCover(finalVideoPath,FILE_SPACE_PARENT+coverPathDB);
		
		
		//保存视频信息到数据库
		Videos videos = new Videos();
		videos.setAudioId(bgmId);
		videos.setUserId(userId);
		videos.setVideoSeconds((float)videoSeconds);
		videos.setVideoHeight(videoHeight);
		videos.setVideoWidth(videoWidth);
		videos.setVideoDesc(desc);
		videos.setVideoPath(uploadPathDB);
		videos.setCoverPath(coverPathDB);
		videos.setStatus(VideoStatusEnum.SUCCESS.value);
		videos.setCreateTime(new Date());
		String videoId = videoService.saveVideo(videos);
		return IMoocJSONResult.ok(videoId);

	}

	
	
	@ApiOperation(value = "上传封面", notes = "上传封面的接口")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "videoId", value = "视频id", required = true, dataType = "String", paramType = "form"),
			@ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "form"),
	})

	@PostMapping(value = "/uploadCover", headers = "content-type=multipart/form-data")
	public IMoocJSONResult uploadCover(String videoId,
			String userId,
			@ApiParam(value = "短视频", required = true) MultipartFile file) throws Exception {
		if (StringUtils.isBlank(videoId)||StringUtils.isBlank(userId)) {
			return IMoocJSONResult.errorMap("视频主键id和用户id不能为空...");
		}

		// 保存到数据库中的相对路径
		String uploadPathDB = "/" + userId + "/video";
		FileOutputStream fileOutputStream = null;
		InputStream inputStream = null;
		String finalCoverPath = "";
		try {
			if (file != null) {
				String filename = file.getOriginalFilename();
				if (StringUtils.isNoneBlank(filename)) {
					// 文件上传的最终路径
					finalCoverPath = FILE_SPACE + uploadPathDB + "/" + filename;
					// 设置数据库的保存路径
					uploadPathDB = (FILE_SPACE_CHILD + uploadPathDB + "/" + filename);

					File outFile = new File(finalCoverPath);
					if (outFile.getParentFile() != null || !outFile.getParentFile().isDirectory()) {
						// 创建父文件夹
						outFile.getParentFile().mkdirs();
					}

					fileOutputStream = new FileOutputStream(outFile);
					inputStream = file.getInputStream();
					IOUtils.copy(inputStream, fileOutputStream);
				} else {
					return IMoocJSONResult.errorMap("上传出错...");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return IMoocJSONResult.errorMap("上传出错...");
		} finally {
			if (fileOutputStream != null) {
				fileOutputStream.flush();
				fileOutputStream.close();
			}
		}
		System.out.println("uploadPathDB = " + uploadPathDB);
		System.out.println("finalCoverPath = " + finalCoverPath);
		videoService.updateVideo(videoId, uploadPathDB);
		
		return IMoocJSONResult.ok();

	}
	
	/**
	 * 
	 * 分页和搜索查询视频列表
	 * @param isSaveRecord 1-需要保存，
	 *                     0-null不需要保存
	 */
	@PostMapping(value = "/showAll")
	public IMoocJSONResult showALl(@RequestBody Videos video, Integer isSaveRecord, Integer page) throws Exception {
		
		if(page == null) {
			page = 1;
		}
	
		PagedResult pagedResult = videoService.getAllVides(video,isSaveRecord,page, PAGE_SIZE);
		return IMoocJSONResult.ok(pagedResult);

	}
	
	@PostMapping(value = "/hot")
	public IMoocJSONResult hot() throws Exception {
		
		return IMoocJSONResult.ok(videoService.getHotwords());

	}
	
	@PostMapping(value = "/userLike")
	public IMoocJSONResult userLike(String userId, String videoId, String videoCreaterId) throws Exception {
		
		videoService.userLikeVideo(userId, videoId, videoCreaterId);
		return IMoocJSONResult.ok();

	}
	
	@PostMapping(value = "/userUnLike")
	public IMoocJSONResult userUnLike(String userId, String videoId, String videoCreaterId) throws Exception {
		videoService.userUnLikeVideo(userId, videoId, videoCreaterId);
		return IMoocJSONResult.ok();

	}
	
	@PostMapping(value = "/showMyLike")
	public IMoocJSONResult showMyLike(String userId, Integer page, Integer pageSize) throws Exception {
       if(StringUtils.isBlank(userId)) {
    	   return IMoocJSONResult.errorMsg("");
       }
       
       if(page == null) {
    	   page = 1;
       }
       
       if(pageSize == null) {
    	   pageSize = PAGE_SIZE;
       }
		
       PagedResult videoList = videoService.queryMyLikeVideos(userId, page, pageSize);
       
		return IMoocJSONResult.ok(videoList);

	}
	
	@PostMapping(value = "/showMyFollow")
	public IMoocJSONResult showMyFollow(String userId, Integer page, Integer pageSize) throws Exception {
       if(StringUtils.isBlank(userId)) {
    	   return IMoocJSONResult.errorMsg("");
       }
       
       if(page == null) {
    	   page = 1;
       }
       
       if(pageSize == null) {
    	   pageSize = PAGE_SIZE;
       }
		
       PagedResult videoList = videoService.queryMyFollowVideos(userId, page, pageSize);
       
		return IMoocJSONResult.ok(videoList);

	}
	
	@PostMapping(value = "/saveComment")
	public IMoocJSONResult saveComment(@RequestBody Comments comment,String fatherCommentId,String toUserId) throws Exception {
        comment.setFatherCommentId(fatherCommentId);
        comment.setToUserId(toUserId);
		videoService.saveComment(comment);
		return IMoocJSONResult.ok();
	}
	
	@PostMapping(value = "/getVideoComments")
	public IMoocJSONResult getVideoComments(String videoId, Integer page, Integer pageSize) throws Exception {
        if(StringUtils.isBlank(videoId)) {
        	return IMoocJSONResult.errorMsg("");
        }
        
        if(page == null) {
        	page = 1;
        }
        if(pageSize == null) {
        	pageSize = PAGE_SIZE;
        }
        PagedResult list = videoService.getAllComments(videoId, page, pageSize);
		
		return IMoocJSONResult.ok(list);
	}
	
}
