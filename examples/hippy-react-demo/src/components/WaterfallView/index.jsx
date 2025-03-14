import React from 'react';
import {
  WaterfallView,
  View,
  StyleSheet,
  Text,
  Dimensions,
  Platform,
  RefreshWrapper,
} from '@hippy/react';

import mockDataTemp from '../../shared/UIStyles/mock';
import Style1 from '../../shared/UIStyles/Style1';
import Style2 from '../../shared/UIStyles/Style2';
import Style5 from '../../shared/UIStyles/Style5';
const mockData = mockDataTemp.filter(item => item.style !== 2);
const styles = StyleSheet.create({
  container: {
    backgroundColor: '#ffffff',
  },
  itemContainer: {
    padding: 12,
  },
  splitter: {
    marginLeft: 12,
    marginRight: 12,
    height: 0.5,
    backgroundColor: '#e5e5e5',
  },
  loading: {
    fontSize: 11,
    color: '#aaaaaa',
    alignSelf: 'center',
  },
  pullContainer: {
    height: 60,
    backgroundColor: 'green',

  },
  pullContent: {
    lineHeight: 60,
    color: 'white',
    height: 60,
    textAlign: 'center',
  },
  pullFooter: {
    flex: 1,
    height: 40,
    backgroundColor: '#4c9afa',
    justifyContent: 'center',
    alignItems: 'center',
  },
});

export default class ListExample extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      dataSource: [],
      pullingText: '继续下拉触发刷新',
      loadingState: '正在加载...',
    };
    this.numberOfColumns = 2;
    this.columnSpacing = 6;
    this.interItemSpacing = 6;
    this.mockFetchData = this.mockFetchData.bind(this);
    this.renderItem = this.renderItem.bind(this);
    this.getItemType = this.getItemType.bind(this);
    this.getItemKey = this.getItemKey.bind(this);
    this.onEndReached = this.onEndReached.bind(this);
    this.onRefresh = this.onRefresh.bind(this);
    this.getRefresh = this.getRefresh.bind(this);
    this.renderPullFooter = this.renderPullFooter.bind(this);
    this.renderBanner = this.renderBanner.bind(this);
    this.getItemStyle = this.getItemStyle.bind(this);
    // TODO: PullHeader is not supported on Android yet
    // this.renderPullHeader = this.renderPullHeader.bind(this);
    // this.onHeaderReleased = this.onHeaderReleased.bind(this);
    // this.onHeaderPulling = this.onHeaderPulling.bind(this);
  }

  async componentDidMount() {
    const dataSource = await this.mockFetchData();
    this.setState({ dataSource });
    // 结束时需主动调用collapsePullHeader
    // this.listView.collapsePullHeader();
  }

  /**
   * 页面加载更多时触发
   * 这里触发加载更多还可以使用 PullFooter 组件，主要看是否需要一个内容加载区。
   * onEndReached 更适合用来无限滚动的场景。
   */
  async onEndReached() {
    const { dataSource } = this.state;
    // ensure that only one fetching task would be running
    if (this.loadMoreDataFlag) {
      return;
    }
    this.loadMoreDataFlag = true;
    this.setState({
      loadingState: '加载更多...',
    });
    let newData = [];
    try {
      newData = await this.mockFetchData();
    } catch (err) {}
    if (newData.length === 0) {
      this.setState({
        loadingState: '没有更多数据',
      });
    }
    const newDataSource = [...dataSource, ...newData];
    this.setState({ dataSource: newDataSource });
    this.loadMoreDataFlag = false;
  }

  // TODO: PullHeader is not supported on Android yet
  // 下拉超过内容高度，松手后触发
  // async onHeaderReleased() {
  //   if (this.fetchingDataFlag) {
  //     return;
  //   }
  //   this.fetchingDataFlag = true;
  //   // eslint-disable-next-line no-console
  //   console.log('onHeaderReleased');
  //   this.setState({
  //     pullingText: '刷新数据中，请稍等，2秒后自动收起',
  //   });
  //   let dataSource = [];
  //   try {
  //     dataSource = await this.mockFetchData();
  //   } catch (err) {}
  //   this.fetchingDataFlag = false;
  //   this.setState({ dataSource }, () => {
  //     // 要主动调用collapsePullHeader关闭pullHeader，否则可能会导致onHeaderReleased事件不能再次触发
  //     this.listView.collapsePullHeader();
  //   });
  // }

  // 渲染 pullHeader 组件，只保留内容即可
  // renderPullHeader() {
  //   const { pullingText } = this.state;
  //   return (
  //       <View style={styles.pullContainer}>
  //         <Text style={styles.pullContent}>{ pullingText }</Text>
  //       </View>
  //   );
  // }

  /**
   * 下拉过程中触发
   * 事件会通过 contentOffset 参数返回拖拽高度，我们已经知道了内容高度，
   * 简单对比一下就可以显示不同的状态。
   * 这里简单处理，其实可以做到更复杂的动态效果。
   */
  // onHeaderPulling(evt) {
  //   if (this.fetchingDataFlag) {
  //     return;
  //   }
  //   // eslint-disable-next-line no-console
  //   console.log('onHeaderPulling', evt.contentOffset);
  //   if (evt.contentOffset > styles.pullContent.height) {
  //     this.setState({
  //       pullingText: '松手，即可触发刷新',
  //     });
  //   } else {
  //     this.setState({
  //       pullingText: '继续下拉，触发刷新',
  //     });
  //   }
  // }

  renderPullFooter() {
    if (this.state.dataSource.length === 0) return null;
    return (<View style={styles.pullFooter}>
      <Text style={{
        color: 'white',
      }}>{this.state.loadingState}</Text>
    </View>);
  }

  async onRefresh() {
    setTimeout(async () => {
      const dataSource = await this.mockFetchData();
      this.setState({ dataSource });
      this.refresh.refreshComplected();
    }, 1000);
  }

  getRefresh() {
    return (
        <View style={{ flex: 1, height: 40, justifyContent: 'center', alignItems: 'center', backgroundColor: '#4c9afa' }}>
          <Text style={{ height: 40, lineHeight: 40, textAlign: 'center', color: 'white' }}>下拉刷新中...</Text>
        </View>
    );
  }

  /**
   * 点击单行后触发
   *
   * @param {number} index - 被点击的索引号
   */
  onClickItem(index) {
    console.log(`item: ${index} is clicked..`);
  }

  getItemType(index) {
    const item = this.state.dataSource[index];
    return item.style;
  }

  getItemKey(index) {
    return `row-${index}`;
  }

  onItemClick(index) {
    console.log('onItemClick', index);
    this.listView.scrollToIndex({ index, animation: true });
  }

  // render banner(it is not supported on Android yet)
  renderBanner() {
    if (Platform.OS === 'android' || this.state.dataSource.length === 0) return null;
    return (<View style={{
      backgroundColor: 'grey',
      height: 100,
      justifyContent: 'center',
      alignItems: 'center',
    }}>
      <Text style={{
        fontSize: 20,
        color: 'white',
        lineHeight: 100,
        height: 100,
      }}>Banner View</Text>
    </View>);
  }

  renderItem(index) {
    const { dataSource } = this.state;
    let styleUI = null;
    const rowData = dataSource[index];
    switch (rowData.style) {
      case 1:
        styleUI = <Style1 itemBean={rowData.itemBean} />;
        break;
      case 2:
        styleUI = <Style2 itemBean={rowData.itemBean} />;
        break;
      case 5:
        styleUI = <Style5 itemBean={rowData.itemBean} />;
        break;
      default:
    }
    return (
        <View
            onClick={() => this.onItemClick(index)}
            style={styles.container}
        >
          <View style={styles.itemContainer}>
            {styleUI}
          </View>
          <View style={styles.splitter} />
        </View>
    );
  }

  /**
   * 获取 mock 数据
   */
  mockFetchData() {
    return new Promise((resolve) => {
      setTimeout(() => {
        const data = [...mockData, ...mockData];
        return resolve(data);
      }, 600);
    });
  }

  getWaterfallContentInset() {
    return { top: 0, left: 5, bottom: 0, right: 5 };
  }

  getItemStyle() {
    const { numberOfColumns, columnSpacing } = this;
    const screenWidth = Dimensions.get('screen').width;
    const contentInset = this.getWaterfallContentInset();
    const width = screenWidth - contentInset.left - contentInset.right;
    return {
      width: (width - ((numberOfColumns - 1) * columnSpacing)) / numberOfColumns,
    };
  }

  render() {
    const { dataSource } = this.state;
    const { numberOfColumns, columnSpacing, interItemSpacing } = this;
    const contentInset = this.getWaterfallContentInset();
    return (
        <RefreshWrapper
            ref={(ref) => {
              this.refresh = ref;
            }}
            style={{ flex: 1 }}
            onRefresh={this.onRefresh}
            bounceTime={100}
            getRefresh={this.getRefresh}
        >
          <WaterfallView
              ref={(ref) => {
                this.listView = ref;
              }}
              renderBanner={this.renderBanner}
              numberOfColumns={numberOfColumns}
              columnSpacing={columnSpacing}
              interItemSpacing={interItemSpacing}
              numberOfItems={dataSource.length}
              style={{ flex: 1 }}
              renderItem={this.renderItem}
              onEndReached={this.onEndReached}
              getItemType={this.getItemType}
              getItemKey={this.getItemKey}
              contentInset={contentInset}
              getItemStyle={this.getItemStyle}
              containPullFooter={true}
              // renderPullHeader={this.renderPullHeader}
              // onHeaderReleased={this.onHeaderReleased}
              // onHeaderPulling={this.onHeaderPulling}
              renderPullFooter={this.renderPullFooter}
          />
        </RefreshWrapper>
    );
  }
}
