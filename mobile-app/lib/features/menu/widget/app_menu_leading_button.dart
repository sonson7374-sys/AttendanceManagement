import 'package:flutter/material.dart';

/// AppBar의 leading 자리에 넣는 메뉴(햄버거) 버튼. 각 화면이 automaticallyImplyLeading을
/// false로 꺼둔 경우에도 항상 화면 제목 앞에 메뉴 아이콘이 보이도록 명시적으로 지정한다.
class AppMenuLeadingButton extends StatelessWidget {
  const AppMenuLeadingButton({super.key});

  @override
  Widget build(BuildContext context) {
    return Builder(
      builder: (context) => IconButton(
        icon: const Icon(Icons.menu),
        tooltip: '메뉴',
        onPressed: () => Scaffold.of(context).openDrawer(),
      ),
    );
  }
}
