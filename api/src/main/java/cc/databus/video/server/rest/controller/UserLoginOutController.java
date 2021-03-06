package cc.databus.video.server.rest.controller;

import cc.databus.common.JsonResponse;
import cc.databus.common.MD5Utils;
import cc.databus.common.UUIDGenerator;
import cc.databus.video.server.service.UserService;
import cc.databus.video.util.RedisOperator;
import cc.databus.videos.server.pojo.Users;
import cc.databus.videos.server.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/")
@Api(value = "用户操作接口", tags = {"注册","登录","修改"})
public class UserLoginOutController {

    private static final String USER_REDIS_SESSION = "user-redis-session";

    @Autowired
    private UserService userService;

    @Autowired
    private RedisOperator redisOperator;

    @ApiOperation(value = "用户注册", notes = "用户注册接口")
    @PostMapping("/register")
    public JsonResponse register(@RequestBody Users user) {
        // 1. username and password not null and empty
        if (StringUtils.isBlank(user.getUsername()) || StringUtils.isBlank(user.getPassword())) {
            return JsonResponse.badRequest("用户名和密码不能为空");
        }

        // 2. username not exist
        if (userService.queryUsernameIfExist(user.getUsername())) {
            return JsonResponse.badRequest(String.format("用户名%s已存在", user.getUsername()));
        }

        // 3. save user and register info
        user.setNickname(user.getUsername());
        user.setPassword(MD5Utils.md5(user.getPassword()));
        user.setFansCounts(0);
        user.setReceiveLikeCounts(0);
        user.setFollowCounts(0);

        userService.saveUser(user);

        UserVO voUser = createUserViewObject(user);
        saveUserSession(user.getId(), voUser.getUserToken());

        return JsonResponse.ok(voUser);
    }

    @ApiOperation(value = "用户登录", notes = "用户登录接口", tags = "登录")
    @PostMapping("/login")
    public JsonResponse login(@RequestBody Users user) {
        String username = user.getUsername();
        String password = user.getPassword();

        // 1. check username and password not null
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return JsonResponse.badRequest("用户名和密码不能为空");
        }

        // 2. check user exist
        Users queryResult = userService.queryUserForLogin(username, MD5Utils.md5(password));

        if (Objects.isNull(queryResult)) {
            return JsonResponse.badRequest("用户不存在或者用户名密码不匹配" );
        }
        else {
            UserVO voUser = createUserViewObject(queryResult);
            saveUserSession(queryResult.getId(), voUser.getUserToken());

            return JsonResponse.ok(voUser);
        }
    }

    @ApiOperation(value = "用户注销", notes = "用户注销接口", tags = "注销")
    @ApiImplicitParam(name = "userId", value = "用户id", required = true, dataType = "String", paramType = "query")
    @PostMapping("/logout")
    public JsonResponse logout(String userId) {
        redisOperator.delete(USER_REDIS_SESSION + ":" + userId);
        return JsonResponse.ok();
    }

    private void saveUserSession(String userId, String uniqueToken) {
        redisOperator.set(USER_REDIS_SESSION + ":" + userId, uniqueToken);
    }

    private UserVO createUserViewObject(Users userModel) {
        String uniqueToken = UUIDGenerator.generate().toUpperCase();
        UserVO voUser = UserVO.fromUserPojo(userModel);
        voUser.setUserToken(uniqueToken);
        // remove sensitive field
        voUser.setPassword("");
        return voUser;
    }
}
