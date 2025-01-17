# 

# EYE-Contact

---

시각장애인 보행을 보조하는 어플로 횡단보도/신호등 탐지 가능

## 개발 배경

---

- **시각장애인 복지관 인터뷰 & 설문조사**
    
    시각장애인 복지관을 방문하여 인터뷰와 설문조사를 진행했다. 인터뷰를 통해 시각장애인이 음향신호기가 없는 횡단보도를 건너기 위해서는 주변 사람에게 도움을 요청해야하며 만약 주변에 아무도 없는 경우에는 신호를 파악할 수 없어 주변 사람을 기다려야 했다. 또한, 설문결과 인원 모두 보행 중 장애물과 부딪힌 경험이 있었다.
    
- **자료 조사**
    
    [2021년 보행환경 만족도]에 따르면 비교통약자에 비해 교통약자는 64.4%로 낮은 보행환경 만족도를 나타내고 있다. 또한, [2021년 시도별 음향신호기 설치 현황]에서는 현재 전국 평균 음향신호기 설치 비율은 33.9%로 미흡한 상황이다.
    

현재 시각장애인은 보행 중 장애물에 부딪히는 경험을 가지고 있으며, 음향신호기 설치가 미흡한 상황으로 보행시 많은 불편함을 겪고 있다. 이를 개선하기 위해 시각장애인 보행 보조 어플을 개발하기로 하였다. ****

⏩ **‘EYE-Contact’는 딥러닝을 활용하여 장애물과 신호등을 탐지하여 음성으로 안내하는 어플**로 **시각장애인의 보행 만족도를 높이고 보행 위험성을 줄이고자 한다.**

## 개발

---

**어플의 주요 기능**

어플의 주요 기능은 장애물 탐지와 신호등 탐지이다. 장애물 탐지 모델과 신호등 탐지 모델을 각각 개발하여 장애물 탐지 모드를 기본 모델로 사용한다. 탐지 장애물에 횡단보도를 추가하여 횡단보도가 탐지되고, 사용자가 건너고 싶다면 화면을 두번 터치하여 신호등 탐지 모델로 변환하여 신호등을 건널 수 있도록 한다.

### 1. 장애물 탐지 모델

시각장애인 복지관 설문조사를 통하여 보행시 가장 위험한 장애물 상위 3개(가로수, 볼라드, 가로등)를 선정하고, 횡단보드를 추가적으로 탐지 장애물로 선정한다.

**활용 데이터** 

- AI-Hub에서 [[인도 보행 영상]](https://www.aihub.or.kr/aihubdata/data/view.do?currMenu=&topMenu=&aihubDataSe=realm&dataSetSn=189) 데이터를 통해 수집 **[[출처 : AI-Hub**]](https://www.aihub.or.kr/)
- 부족한 데이터는 직접 수집한다.

**활용 모델**

빠른 속도와 경량화 모델인 **YOLOv5s** 모델을 활용하여 학습

|  | Instances | Precision | Recall | mAP50 | mAP50-95 |
| --- | --- | --- | --- | --- | --- |
| all | 66822 | 0.908 | 0.897 | 0.949 | 0.716 |
| 볼라드 | 17801 | 0.908 | 0.818 | 0.949 | 0.622 |
| 전봇대 | 25261 | 0.888 | 0.921 | 0.969 | 0.773 |
| 가로수 | 23618 | 0.927 | 0.963 | 0.979 | 0.764 |

### 2. 신호등 탐지 모델

사용자의 안전을 위해 빨간 신호등에서 초록 신호등으로 바뀌는 경우 횡단보도를 횡단할 수 있도록 구현한다. 빨간 신호등에서 초록 신호등으로 바뀌는 경우가 횡단 가능 시간이 가장 길기 때문인다.

**활용데이터 & 모델**

- 출저 깃허브의 중국데이터와 모델을 활용하여 개발 [[출처 : 깃허브]](https://github.com/samuelyu2002/ImVisible)
